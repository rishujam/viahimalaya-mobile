package com.via.himalaya.data.repository

import com.via.himalaya.domain.model.Trek
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.data.models.TreksData
import com.via.himalaya.data.models.TrekDetailData
import com.via.himalaya.data.models.VResponse
import com.via.himalaya.domain.model.TrekDetail
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class TrekRepositoryImpl(
    private val apiClient: HttpClient
) : TrekRepository {

    companion object {
        private const val BASE_URL = "https://viahimalaya.com"
        const val DEFAULT_API_KEY = "ea6265827acc4132d98dc8e37727f36fda3b91e9c4c6d79b4cb5b6c89d9fa6cf"
    }

    override suspend fun getTreks(): Result<List<Trek>> {
        val response = apiClient
            .get("$BASE_URL/api/treks") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer $DEFAULT_API_KEY")
                }
            }
        return if(response.status.value == 200) {
            val trekData = response.body<VResponse<TreksData>>().data
            if(trekData.treks.isEmpty()) {
                Result.Error("No treks found", 204)
            } else {
                Result.Success(trekData.toTreks())
            }

        } else {
            Result.Error(response.status.description, response.status.value)
        }
    }

    override suspend fun getTrekCoordinates(coordinateUrl: String): Result<TrekGeoData> {
        return try {
            val response = apiClient.get(coordinateUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }
            
            if (response.status.value == 200) {
                val geoData = response.body<TrekGeoData>()
                Result.Success(geoData)
            } else {
                Result.Error("Failed to fetch coordinates: ${response.status.description}", response.status.value)
            }
        } catch (e: Exception) {
            Result.Error("Error fetching coordinates: ${e.message}", 500)
        }
    }

    override suspend fun getTrek(id: String): Result<TrekDetail> {
        return try {
            val response = apiClient.get("$BASE_URL/api/treks/$id") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer $DEFAULT_API_KEY")
                }
            }
            
            if (response.status.value == 200) {
                val trekDetailData = response.body<VResponse<TrekDetailData>>().data
                Result.Success(trekDetailData.toTrekDetail())
            } else if (response.status.value == 404) {
                Result.Error("Trek not found", 404)
            } else {
                Result.Error(response.status.description, response.status.value)
            }
        } catch (e: Exception) {
            Result.Error("Error fetching trek details: ${e.message}", 500)
        }
    }
}
