package com.via.himalaya.data.repository

import com.via.himalaya.data.local.CustomPoiDao
import com.via.himalaya.data.models.CustomPoi
import com.via.himalaya.data.models.CustomPoiStatus
import com.via.himalaya.data.models.LocationSource
import com.via.himalaya.data.remote.ApiConfig
import com.via.himalaya.data.remote.HttpClientFactory
import com.via.himalaya.domain.NetworkMonitor
import com.via.himalaya.domain.PhotoStore
import com.via.himalaya.domain.model.UserProfile
import com.via.himalaya.domain.repo.AuthRepository
import com.via.himalaya.util.Result
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Drives the real upload call through the real HttpClientFactory setup, with a
 * fake engine in place of the network, and checks what would actually go out and
 * what happens to the queue on each answer.
 *
 * This is the closest the upload gets to being exercised without a phone: the
 * body shape, the headers and the way the default JSON content type interacts
 * with a multipart body are all things a mistake in would only show up as a 400
 * from the server.
 */
class CustomPoiRepositoryTest {

    private val photoBytes = ByteArray(200) { (it * 7).toByte() }

    // ---------- fakes

    private class FakeDao : CustomPoiDao {
        val rows = linkedMapOf<String, CustomPoi>()
        override suspend fun insert(poi: CustomPoi) { rows[poi.id] = poi }
        override suspend fun getPending() =
            rows.values.filter { it.status == CustomPoiStatus.PENDING }.sortedBy { it.createdAt }
        override suspend fun markUploaded(id: String) {
            rows[id] = rows.getValue(id).copy(status = CustomPoiStatus.UPLOADED, attempts = rows.getValue(id).attempts + 1, lastError = null)
        }
        override suspend fun markFailed(id: String, error: String) {
            rows[id] = rows.getValue(id).copy(status = CustomPoiStatus.FAILED, attempts = rows.getValue(id).attempts + 1, lastError = error)
        }
        override suspend fun recordAttempt(id: String, error: String) {
            rows[id] = rows.getValue(id).copy(attempts = rows.getValue(id).attempts + 1, lastError = error)
        }
    }

    private class FakePhotos : PhotoStore {
        val files = mutableMapOf<String, ByteArray>()
        val deleted = mutableListOf<String>()
        override suspend fun prepare(sourceUri: String, id: String): String? = null
        override suspend fun read(path: String) = files[path]
        override suspend fun delete(path: String) { files.remove(path); deleted += path }
    }

    private class FakeAuth(var user: UserProfile? = UserProfile(email = "a@x.com"), var token: () -> String? = { "tok-123" }) : AuthRepository {
        override suspend fun signInWithGoogle(idToken: String): Result<UserProfile> = Result.Error("unused", -1)
        override suspend fun signOut() {}
        override suspend fun getCurrentUser() = user
        override suspend fun getIdToken() = token()
    }

    /** Hands each request to [respond]; the engine itself enforces nothing. */
    @OptIn(InternalAPI::class)
    private class FakeEngine(val respond: suspend (HttpRequestData) -> HttpStatusCode) : HttpClientEngineBase("fake") {
        override val config = HttpClientEngineConfig()
        override val supportedCapabilities = setOf(HttpTimeoutCapability)
        val requests = mutableListOf<HttpRequestData>()

        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            requests += data
            val status = respond(data)
            return HttpResponseData(
                statusCode = status,
                requestTime = GMTDate(),
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                version = HttpProtocolVersion.HTTP_1_1,
                body = ByteReadChannel("""{"success":${status.value in 200..299},"error":"${status.description}"}"""),
                callContext = callContext()
            )
        }
    }

    // ---------- setup

    private lateinit var dao: FakeDao
    private lateinit var photos: FakePhotos
    private lateinit var auth: FakeAuth
    private var online = true
    private lateinit var engine: FakeEngine
    private lateinit var repository: CustomPoiRepositoryImpl

    @BeforeTest
    fun setUp() {
        dao = FakeDao()
        photos = FakePhotos()
        auth = FakeAuth()
        online = true
        useEngine { HttpStatusCode.Created }
    }

    private fun useEngine(respond: suspend (HttpRequestData) -> HttpStatusCode) {
        engine = FakeEngine(respond)
        repository = CustomPoiRepositoryImpl(
            apiClient = HttpClientFactory.create(engine),
            apiConfig = ApiConfig(baseUrl = "https://example.test", apiKey = "shared-key"),
            customPoiDao = dao,
            photoStore = photos,
            networkMonitor = NetworkMonitor { online },
            authRepository = auth
        )
    }

    private fun poi(id: String, at: Long = 1L, withPhoto: Boolean = true): CustomPoi {
        val path = "/photos/$id.jpg"
        if (withPhoto) photos.files[path] = photoBytes
        return CustomPoi(
            id = id, trekId = "goechala_0", lat = 27.4327, lon = 88.18163,
            locationSource = LocationSource.GPS, accuracyM = 6.5, description = "Camp by the stream",
            photoPath = path, termsVersion = "2026-10-29", createdAt = at
        )
    }

    private fun save(vararg pois: CustomPoi) = runBlocking { pois.forEach { dao.insert(it) } }
    private fun sync() = runBlocking { repository.syncPending() }
    private fun status(id: String) = dao.rows.getValue(id).status

    /** The bytes of a request body, read back out of the engine's hands. */
    private fun bodyOf(request: HttpRequestData): ByteArray = runBlocking {
        when (val content = request.body) {
            is OutgoingContent.ByteArrayContent -> content.bytes()
            is OutgoingContent.WriteChannelContent -> coroutineScope {
                val channel = ByteChannel()
                launch { content.writeTo(channel); channel.close() }
                channel.toByteArray()
            }
            else -> error("unexpected body type ${content::class}")
        }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    // ---------- what goes out

    @Test
    fun theRequestCarriesTheLoginTokenAndAMultipartBodyWithMetaAndPhoto() {
        save(poi("11111111-1111-4111-8111-111111111111"))
        sync()

        val request = engine.requests.single()
        assertEquals("POST", request.method.value)
        assertEquals("https://example.test/api/custom-pois", request.url.toString())

        // The user's login, not the app's shared key.
        assertEquals("Bearer tok-123", request.headers[HttpHeaders.Authorization])

        // The client's default is JSON; a multipart body has to win over it.
        val contentType = request.body.contentType.toString()
        assertTrue(contentType.startsWith("multipart/form-data"), "was $contentType")
        assertTrue(contentType.contains("boundary="), "was $contentType")

        val body = bodyOf(request)
        val text = body.decodeToString(throwOnInvalidSequence = false)
        // Quoted names, and a filename on the photo. Cloudflare's runtime refuses
        // `name=meta` ("missing a name"), and reads a part without a filename as
        // a plain string instead of a file. Both were checked against workerd.
        assertTrue(text.contains("Content-Disposition: form-data; name=\"meta\"\r\n"), "meta part missing or unquoted\n$text")
        assertTrue(
            text.contains("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\n"),
            "photo part missing, unquoted, or without a filename"
        )
        assertTrue(text.contains("Content-Type: image/jpeg"), "photo content type missing")

        // Sent with a Content-Length that is right, not chunked.
        assertEquals(body.size.toLong(), request.body.contentLength, "Content-Length must match the body")

        // The meta part is the JSON the server's parseMeta expects.
        for (expected in listOf(
            "\"id\":\"11111111-1111-4111-8111-111111111111\"",
            "\"trekId\":\"goechala_0\"",
            "\"lat\":27.4327",
            "\"lon\":88.18163",
            "\"locationSource\":\"gps\"",
            "\"accuracyM\":6.5",
            "\"description\":\"Camp by the stream\"",
            "\"termsVersion\":\"2026-10-29\""
        )) {
            assertTrue(text.contains(expected), "meta is missing $expected\n$text")
        }
        // Nobody is named in the body: the server takes the author from the token.
        assertTrue(!text.contains("userId") && !text.contains("user_id"))

        // The photo's bytes travel intact.
        assertTrue(indexOf(body, photoBytes) >= 0, "the photo bytes are not in the body")
    }

    @Test
    fun successMarksItUploadedAndDeletesThePhoneCopy() {
        save(poi("a"))
        sync()
        assertEquals(CustomPoiStatus.UPLOADED, status("a"))
        assertEquals(listOf("/photos/a.jpg"), photos.deleted)
    }

    @Test
    fun anAlreadyHadItAnswerIsSuccessToo() {
        useEngine { HttpStatusCode.OK }
        save(poi("a"))
        sync()
        assertEquals(CustomPoiStatus.UPLOADED, status("a"))
    }

    // ---------- what happens to the queue

    @Test
    fun aRefusalIsFinalButTheNextPlaceStillGoes() {
        var n = 0
        useEngine { if (n++ == 0) HttpStatusCode.UnprocessableEntity else HttpStatusCode.Created }
        save(poi("a", 1), poi("b", 2))
        sync()

        assertEquals(CustomPoiStatus.FAILED, status("a"))
        assertTrue(dao.rows.getValue("a").lastError!!.startsWith("HTTP 422"))
        // Kept, so a mistake on our side does not destroy what someone sent.
        assertTrue("/photos/a.jpg" in photos.files)
        assertEquals(CustomPoiStatus.UPLOADED, status("b"))
        assertEquals(2, engine.requests.size)
    }

    @Test
    fun serverTroubleKeepsItAndStopsTheRun() {
        useEngine { HttpStatusCode.InternalServerError }
        save(poi("a", 1), poi("b", 2))
        sync()

        assertEquals(CustomPoiStatus.PENDING, status("a"))
        assertEquals(CustomPoiStatus.PENDING, status("b"))
        assertEquals(1, engine.requests.size, "what failed would fail for every place")
        assertEquals(1, dao.rows.getValue("a").attempts)
        assertTrue("/photos/a.jpg" in photos.files)
    }

    @Test
    fun noValidLoginAndTheDailyLimitAreKeptForLater() {
        for (answer in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.TooManyRequests)) {
            setUp()
            useEngine { answer }
            save(poi("a"))
            sync()
            assertEquals(CustomPoiStatus.PENDING, status("a"), "HTTP ${answer.value}")
        }
    }

    @Test
    fun noConnectionMidUploadKeepsItAndStops() {
        useEngine { throw IOException("Unable to resolve host") }
        save(poi("a", 1), poi("b", 2))
        sync()

        assertEquals(CustomPoiStatus.PENDING, status("a"))
        assertEquals(1, dao.rows.getValue("a").attempts)
        assertTrue(dao.rows.getValue("a").lastError!!.contains("resolve host"))
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun offlineSendsNothing() {
        online = false
        save(poi("a"))
        sync()
        assertEquals(0, engine.requests.size)
        assertEquals(CustomPoiStatus.PENDING, status("a"))
    }

    @Test
    fun signedOutSendsNothing() {
        auth.user = null
        save(poi("a"))
        sync()
        assertEquals(0, engine.requests.size)
        assertEquals(CustomPoiStatus.PENDING, status("a"))
    }

    @Test
    fun anExpiredTokenWithNoNetworkDoesNotLoseThePlaceOrCrash() {
        auth.token = { throw IOException("A network error") }
        save(poi("a"))
        sync()
        assertEquals(0, engine.requests.size)
        assertEquals(CustomPoiStatus.PENDING, status("a"))
    }

    @Test
    fun noTokenAtAllMeansNothingIsSent() {
        auth.token = { null }
        save(poi("a"))
        sync()
        assertEquals(0, engine.requests.size)
        assertEquals(CustomPoiStatus.PENDING, status("a"))
    }

    @Test
    fun aPhotoThatIsGoneIsFailedAndTheRestCarryOn() {
        save(poi("a", 1, withPhoto = false), poi("b", 2))
        sync()
        assertEquals(CustomPoiStatus.FAILED, status("a"))
        assertEquals("The photo file is missing", dao.rows.getValue("a").lastError)
        assertEquals(CustomPoiStatus.UPLOADED, status("b"))
        assertEquals(1, engine.requests.size)
    }

    @Test
    fun placesAreSentOldestFirst() {
        save(poi("newer", 20), poi("older", 10))
        sync()
        val order = engine.requests.map { bodyOf(it).decodeToString(throwOnInvalidSequence = false) }
            .map { Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1] }
        assertEquals(listOf("older", "newer"), order)
    }

    @Test
    fun aPlaceSavedWhileARunIsGoingIsPickedUpByThatRun() {
        var injected = false
        useEngine {
            if (!injected) {
                injected = true
                // The user taps Save on a second place while the first is uploading.
                dao.rows["late"] = poi("late", 99)
            }
            HttpStatusCode.Created
        }
        save(poi("first", 1))
        sync()
        assertEquals(CustomPoiStatus.UPLOADED, status("first"))
        assertEquals(CustomPoiStatus.UPLOADED, status("late"))
        assertEquals(2, engine.requests.size)
    }

    @Test
    fun aRunThatArrivesWhileAnotherIsGoingDoesNotSendAnythingTwice() {
        // The first request is held open; a second sync starts meanwhile.
        runBlocking {
            save(poi("a"))
            var secondRunFinished = false
            useEngine {
                // Re-enter while the first run holds the lock: must return at once.
                repository.syncPending()
                secondRunFinished = true
                HttpStatusCode.Created
            }
            repository.syncPending()
            assertTrue(secondRunFinished)
            assertEquals(1, engine.requests.size)
            assertEquals(CustomPoiStatus.UPLOADED, status("a"))
        }
    }

    @Test
    fun savingWritesAPendingRowAndNothingElse() {
        runBlocking { repository.save(poi("a")) }
        assertEquals(CustomPoiStatus.PENDING, status("a"))
        assertEquals(0, engine.requests.size)
        assertNotNull(dao.rows["a"])
        assertNull(dao.rows["a"]!!.lastError)
        assertContentEquals(photoBytes, photos.files["/photos/a.jpg"])
    }
}
