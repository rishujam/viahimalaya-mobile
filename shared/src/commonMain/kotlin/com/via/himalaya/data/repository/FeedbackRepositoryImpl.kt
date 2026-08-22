package com.via.himalaya.data.repository

import com.via.himalaya.data.models.FeedbackRequest
import com.via.himalaya.data.remote.ApiConfig
import com.via.himalaya.domain.repo.FeedbackRepository
import com.via.himalaya.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlin.coroutines.cancellation.CancellationException

class FeedbackRepositoryImpl(
    private val apiClient: HttpClient,
    private val apiConfig: ApiConfig
) : FeedbackRepository {

    override suspend fun submitFeedback(feedbackId: String, feedback: String): Result<Unit> {
        return try {
            val response = apiClient.post("${apiConfig.baseUrl}/api/feedback") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer ${apiConfig.apiKey}")
                }
                setBody(FeedbackRequest(feedbackId = feedbackId, feedback = feedback))
            }

            if (response.status.value == 200) {
                Result.Success(Unit)
            } else {
                Result.Error(response.status.description, response.status.value)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unlike app config, this failure has a user waiting on it, so it
            // is reported rather than swallowed - they typed something and need
            // to know whether it went anywhere.
            Result.Error(e.message ?: "Could not send feedback", 0)
        }
    }
}
