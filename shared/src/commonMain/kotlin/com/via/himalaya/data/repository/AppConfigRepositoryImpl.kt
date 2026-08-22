package com.via.himalaya.data.repository

import com.via.himalaya.data.models.AppConfigDto
import com.via.himalaya.data.models.VResponse
import com.via.himalaya.data.models.toDomain
import com.via.himalaya.data.remote.ApiConfig
import com.via.himalaya.domain.model.AppConfig
import com.via.himalaya.domain.repo.AppConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Remote config over one unauthenticated GET.
 *
 * Held in memory for the life of the process and deliberately not persisted.
 * Caching it would let a banner appear offline whose dialog cannot submit, and
 * it would add a third copy of the freshness problem that poi_updated_at
 * already caused once. Losing config on a cold start costs nothing: the only
 * consequence is that a banner appears a moment later, or not at all.
 */
class AppConfigRepositoryImpl(
    private val apiClient: HttpClient,
    private val apiConfig: ApiConfig
) : AppConfigRepository {

    private val _config = MutableStateFlow(AppConfig())
    override val config: StateFlow<AppConfig> = _config.asStateFlow()

    override suspend fun refresh() {
        try {
            val response = apiClient.get("${apiConfig.baseUrl}/api/app-config") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
                // No Authorization header, unlike every other call in this app.
                // /api/app-config is intentionally open: the shared key is
                // recoverable from any APK so it buys no secrecy here, and it is
                // due to be rotated alongside per-user auth - which would 401
                // every installed build. Config is the one channel that has to
                // survive that rotation, because it is how those builds get told
                // to update.
            }

            if (response.status.value == 200) {
                _config.value = response.body<VResponse<AppConfigDto>>().data.toDomain()
            }
            // Any other status leaves the previous value in place. There is no
            // error state to enter: an empty config and a failed fetch are the
            // same thing to every consumer.

        } catch (e: CancellationException) {
            // Rethrown so cancelling the caller's scope actually cancels. The
            // broad catch below would otherwise swallow it and leave the
            // coroutine looking like it completed normally.
            throw e
        } catch (e: Exception) {
            // Swallowed on purpose. Launch-time config has no user waiting on
            // it and nothing to interrupt; the banner simply does not appear.
        }
    }
}
