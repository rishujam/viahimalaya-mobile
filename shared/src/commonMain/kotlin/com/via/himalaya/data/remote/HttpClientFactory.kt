package com.via.himalaya.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {
    fun create(engine: HttpClientEngine): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(
                    json = Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
            install(HttpTimeout) {
                socketTimeoutMillis = 10_000L
                requestTimeoutMillis = 10_000L
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        println(message)
                    }
                }
                level = LogLevel.ALL
                // Logging a photo upload would print the user's login token and
                // copy the whole picture into logcat, so that one call is left out.
                filter { request ->
                    !request.url.encodedPath.startsWith("/api/custom-pois")
                }
                // And no request's Authorization header is worth keeping in a log:
                // the shared key is recoverable from the APK, but a login token is
                // a person.
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }
    }
}

