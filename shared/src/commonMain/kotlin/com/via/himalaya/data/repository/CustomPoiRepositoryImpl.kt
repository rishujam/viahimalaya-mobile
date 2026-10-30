package com.via.himalaya.data.repository

import com.via.himalaya.data.local.CustomPoiDao
import com.via.himalaya.data.models.CustomPoi
import com.via.himalaya.data.models.CustomPoiUploadMeta
import com.via.himalaya.data.models.toUploadMeta
import com.via.himalaya.data.remote.ApiConfig
import com.via.himalaya.domain.NetworkMonitor
import com.via.himalaya.domain.PhotoStore
import com.via.himalaya.domain.repo.AuthRepository
import com.via.himalaya.domain.repo.CustomPoiRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * What to do with a place after the server has answered, by status code. It is
 * the app's half of the contract written at the top of the server route
 * (web repo, app/api/custom-pois/route.ts).
 */
internal enum class UploadOutcome {
    /** The server has it. */
    SENT,

    /** Nothing is wrong with the place; try again later, and stop trying others now. */
    TRY_LATER,

    /** The server refused this exact request and always will. */
    GIVE_UP;

    companion object {
        fun of(status: Int): UploadOutcome = when (status) {
            // 200 is "already had it": a retry of something that landed.
            200, 201 -> SENT
            // Bad field, unknown trek, id taken, too large, not a JPEG, outside
            // the trekking area. Retrying the same bytes cannot change any of them.
            400, 404, 409, 413, 415, 422 -> GIVE_UP
            // 401 (no valid login right now), 429 (daily limit), 5xx, and anything
            // unexpected: not the place's fault, so it is kept for another time.
            else -> TRY_LATER
        }
    }
}

class CustomPoiRepositoryImpl(
    private val apiClient: HttpClient,
    private val apiConfig: ApiConfig,
    private val customPoiDao: CustomPoiDao,
    private val photoStore: PhotoStore,
    private val networkMonitor: NetworkMonitor,
    private val authRepository: AuthRepository
) : CustomPoiRepository {

    private companion object {
        /**
         * The client-wide timeout is 10 seconds, which is right for a JSON call
         * and wrong for a photo: under 1 MB over one bar of signal in the hills
         * can take well over that. A try that times out is only wasted data.
         */
        const val UPLOAD_TIMEOUT_MS = 90_000L

        const val MAX_RECORDED_ERROR_CHARS = 300
    }

    /**
     * Owned here rather than borrowed from a screen. The repository is a
     * singleton, so this lives as long as the process, and an upload started
     * from the trek screen is not cancelled when the user walks away from it.
     * The handler keeps anything unexpected from taking the app down - there is
     * nobody to show an error to, and the place stays queued.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            println("CustomPoiRepository: sync failed: ${error.message}")
        }
    )

    private val syncLock = Mutex()
    private val json = Json

    override suspend fun save(poi: CustomPoi) {
        customPoiDao.insert(poi)
    }

    override fun requestSync() {
        scope.launch { syncPending() }
    }

    override suspend fun syncPending() {
        // One run at a time. The sync after a save and the one on app start can
        // overlap, and two runs would send the same photo twice. The server
        // would collapse the duplicate, but it is someone's mobile data.
        //
        // A run that arrives while another is going does not wait - it relies
        // on the running one to pick its place up, which uploadAll does by
        // re-reading the queue until nothing new is left.
        if (!syncLock.tryLock()) return
        try {
            uploadAll()
        } finally {
            syncLock.unlock()
        }
    }

    private suspend fun uploadAll() {
        if (!networkMonitor.isOnline()) return
        // A local read, so it is safe on the way in. Nobody signed in means no
        // token to send, and the place waits until someone is.
        if (authRepository.getCurrentUser() == null) return

        val tried = mutableSetOf<String>()
        while (true) {
            // Re-read each time round: a place saved while this run was going is
            // picked up here instead of waiting for the next launch.
            val next = customPoiDao.getPending().firstOrNull { it.id !in tried } ?: return
            tried += next.id
            if (!upload(next)) return
        }
    }

    /** Returns false when the run should stop: what failed would fail for every place. */
    private suspend fun upload(poi: CustomPoi): Boolean {
        val photo = photoStore.read(poi.photoPath)
        if (photo == null) {
            // Cleared storage, or a restore onto another phone. There is nothing
            // to send and there never will be.
            customPoiDao.markFailed(poi.id, "The photo file is missing")
            return true
        }

        // May throw offline once the cached token has expired (see
        // AuthRepository.getIdToken). That is not a reason to lose the place.
        val token = try {
            authRepository.getIdToken()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return false

        val meta = json.encodeToString(CustomPoiUploadMeta.serializer(), poi.toUploadMeta())

        val response: HttpResponse = try {
            apiClient.submitFormWithBinaryData(
                url = "${apiConfig.baseUrl}/api/custom-pois",
                formData = multipartParts(meta, photo)
            ) {
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    // The user's own login, not the app's shared key: this is the
                    // one call that says who it is from.
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                timeout {
                    requestTimeoutMillis = UPLOAD_TIMEOUT_MS
                    socketTimeoutMillis = UPLOAD_TIMEOUT_MS
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            customPoiDao.recordAttempt(poi.id, (e.message ?: "No connection").take(MAX_RECORDED_ERROR_CHARS))
            return false
        }

        val status = response.status.value
        return when (UploadOutcome.of(status)) {
            UploadOutcome.SENT -> {
                customPoiDao.markUploaded(poi.id)
                // The server has it; the phone's copy is only taking up room.
                photoStore.delete(poi.photoPath)
                true
            }

            UploadOutcome.GIVE_UP -> {
                customPoiDao.markFailed(poi.id, describe(status, response))
                // Not the next place's problem - carry on with the rest.
                true
            }

            UploadOutcome.TRY_LATER -> {
                customPoiDao.recordAttempt(poi.id, describe(status, response))
                false
            }
        }
    }

    /**
     * The two parts of the upload, written out by hand.
     *
     * Ktor's formData builder would do this, but it writes the field name bare -
     * `name=meta` - and Cloudflare's runtime refuses a part like that: "Content-
     * Disposition header in FormData part is missing a name". Browsers always
     * quote it, and so does the form parser on the other end expect. Found by
     * running the builder's output through workerd; the 400 it produced would
     * have been read by the queue as "refused for good".
     *
     * The lengths are stated so the request goes out with a Content-Length
     * instead of being chunked.
     */
    private fun multipartParts(meta: String, photo: ByteArray): List<PartData> = listOf(
        PartData.FormItem(
            value = meta,
            dispose = {},
            partHeaders = Headers.build {
                append(HttpHeaders.ContentDisposition, "form-data; name=\"meta\"")
                append(HttpHeaders.ContentLength, meta.encodeToByteArray().size.toString())
            }
        ),
        PartData.BinaryChannelItem(
            provider = { ByteReadChannel(photo) },
            partHeaders = Headers.build {
                // A filename is what makes the server read this part as a file.
                append(HttpHeaders.ContentDisposition, "form-data; name=\"photo\"; filename=\"photo.jpg\"")
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentLength, photo.size.toString())
            }
        )
    )

    /** "HTTP 422: That point is outside the trekking area" - what the server said, for debugging. */
    private suspend fun describe(status: Int, response: HttpResponse): String {
        val body = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        return "HTTP $status: $body".take(MAX_RECORDED_ERROR_CHARS)
    }
}
