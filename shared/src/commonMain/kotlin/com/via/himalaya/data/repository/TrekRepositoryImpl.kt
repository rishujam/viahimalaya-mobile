package com.via.himalaya.data.repository

import com.via.himalaya.data.local.FileDownloader
import com.via.himalaya.data.local.NavigatorDao
import com.via.himalaya.data.local.TrekDao
import com.via.himalaya.data.models.NavigatorTrek
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.data.models.TrekDetailData
import com.via.himalaya.data.models.TrekSearchData
import com.via.himalaya.data.models.TreksData
import com.via.himalaya.data.models.VResponse
import com.via.himalaya.data.local.OfflineMapManager
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.domain.model.TrekGeometry
import com.via.himalaya.domain.model.Treks
import com.via.himalaya.domain.model.getFlattenedCoordinates
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TrekRepositoryImpl(
    private val apiClient: HttpClient,
    private val trekDao: TrekDao,
    private val navigatorDao: NavigatorDao,
    private val fileDownloader: FileDownloader,
    private val offlineMapManager: OfflineMapManager?,
) : TrekRepository {

    companion object {
        private const val BASE_URL = "https://viahimalaya.com"
        const val DEFAULT_API_KEY = "ea6265827acc4132d98dc8e37727f36fda3b91e9c4c6d79b4cb5b6c89d9fa6cf"
        // Zoom levels for offline maps
        // IMPORTANT: Mapbox tile packs have predefined zoom ranges: 0-5, 6-10, 11-14, 15-16
        // Maximum offline zoom is 16 (zoom 17-22 only available online via dynamic tile generation)
        // Setting minZoom=11, maxZoom=16 downloads tile packs for ranges 11-14 and 15-16
        // This provides the best possible offline quality (~30-60MB per trek)
        private const val MIN_ZOOM = 11
        private const val MAX_ZOOM = 16  // Maximum possible for offline maps
    }

    override suspend fun getTreks(
        page: Int,
        limit: Int,
        seed: String?
    ): Result<Treks> {
        return try {
            val response = apiClient
                .get("$BASE_URL/api/treks") {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                        append(HttpHeaders.Authorization, "Bearer $DEFAULT_API_KEY")
                    }
                    url {
                        parameters.append("page", page.toString())
                        parameters.append("limit", limit.toString())
                        seed?.let { parameters.append("seed", it) }
                    }
                }
            if(response.status.value == 200) {
                val trekData = response.body<VResponse<TreksData>>().data
                if(trekData.treks.isEmpty()) {
                    Result.Error("No treks found", 204)
                } else {
                    Result.Success(trekData.toTreks())
                }

            } else {
                Result.Error(response.status.description, response.status.value)
            }
        } catch (e: Exception) {
            Result.Error(e.message.toString(), 503)
        }
    }

    override suspend fun getTrekCoordinates(
        coordinateUrl: String,
        trekId: String
    ): Result<TrekGeoData> {
        return try {
            if (fileDownloader.fileExists(trekId)) {
                println("TrekRepository: Loading coordinates from local storage for trek: $trekId")
                val localData = fileDownloader.readFile(trekId)
                if (localData != null) {
                    try {
                        val geoData = Json.decodeFromString<TrekGeoData>(localData)
                        println("TrekRepository: Successfully loaded coordinates from local storage")
                        return Result.Success(geoData)
                    } catch (e: Exception) {
                        println("TrekRepository: Failed to parse local coordinates: ${e.message}")
                    }
                }
            }

            println("TrekRepository: Fetching coordinates from network for trek: $trekId")
            val response = apiClient.get(coordinateUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }
            if (response.status.value == 200) {
                val geoData = response.body<TrekGeoData>()
                try {
                    fileDownloader.downloadFile(coordinateUrl, trekId)
                    println("TrekRepository: Coordinates cached locally for trek: $trekId")
                } catch (e: Exception) {
                    println("TrekRepository: Failed to cache coordinates: ${e.message}")
                }

                Result.Success(geoData)
            } else {
                Result.Error("Failed to fetch coordinates: ${response.status.description}", response.status.value)
            }
        } catch (e: Exception) {
            Result.Error("Error fetching coordinates: ${e.message}", 500)
        }
    }

    override suspend fun searchTreks(query: String): Result<List<Trek>> {
        return try {
            val response = apiClient.get("$BASE_URL/api/treks/search") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer $DEFAULT_API_KEY")
                }
                url {
                    parameters.append("q", query)
                }
            }
            
            when (response.status.value) {
                200 -> {
                    val searchData = response.body<VResponse<TrekSearchData>>().data
                    if (searchData.treks.isEmpty()) {
                        Result.Error("No treks found matching '$query'", 204)
                    } else {
                        Result.Success(searchData.toTrekList())
                    }
                }
                400 -> {
                    Result.Error("Invalid search query", 400)
                }
                else -> {
                    Result.Error(response.status.description, response.status.value)
                }
            }
        } catch (e: Exception) {
            Result.Error("Error searching treks: ${e.message}", 500)
        }
    }

    override suspend fun getTrek(id: String): Result<TrekDetail> {
        return try {
            val localTrek = trekDao.getTrek(id)
            localTrek?.let {
                Result.Success(localTrek)
            } ?: run {
                val response = apiClient.get("$BASE_URL/api/treks/$id") {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                        append(HttpHeaders.Authorization, "Bearer $DEFAULT_API_KEY")
                    }
                }
                return when (response.status.value) {
                    200 -> {
                        val trekDetailData = response.body<VResponse<TrekDetailData>>().data
                        Result.Success(trekDetailData.toTrekDetail())
                    }
                    404 -> {
                        Result.Error("Trek not found", 404)
                    }
                    else -> {
                        Result.Error(response.status.description, response.status.value)
                    }
                }
            }

        } catch (e: Exception) {
            Result.Error("Error fetching trek details: ${e.message}", 500)
        }
    }

    override suspend fun getDownloadedTreks(): Result<List<TrekDetail>> {
        try {
            val treks = trekDao.getTreks()
            return treks?.let {
                Result.Success(treks)
            } ?: run {
                Result.Error("No treks found", 204)
            }
        } catch (e: Exception) {
            return Result.Error("Error Loading Treks: ${e.message}", 400)
        }
    }

    override suspend fun downloadTrekOffline(
        trek: TrekDetail,
        onProgress: (Float) -> Unit
    ): Result<Boolean> {
        return try {
            println("TrekRepository: Starting offline download for trek: ${trek.id}")
            onProgress(0.1f)
            if (offlineMapManager != null) {
                val coordinatesResult = getTrekCoordinates(trek.coordinateUrl, trek.id)
                if (coordinatesResult !is Result.Success) {
                    return Result.Error(
                        "Failed to download coordinates: ${(coordinatesResult as? Result.Error)?.message}",
                        500
                    )
                }
                onProgress(0.2f)
                println("TrekRepository: Trek coordinates downloaded")
                val coordinatesJson = fileDownloader.readFile(trek.id)
                    ?: return Result.Error("Failed to read coordinates file for tile download", 500)
                val tilesResult = offlineMapManager.downloadTrekTiles(
                    trekId = trek.id,
                    coordinatesJson = coordinatesJson,
                    minZoom = MIN_ZOOM,
                    maxZoom = MAX_ZOOM,
                    onProgress = { tileProgress ->
                        onProgress(0.2f + (tileProgress * 0.7f))
                    }
                )
                if (tilesResult.isSuccess) {
                    onProgress(0.9f)
                    trekDao.insert(trek)
                    onProgress(1.0f)
                    println("TrekRepository: Trek tiles downloaded successfully")
                    Result.Success(true)
                } else {
                    val error = tilesResult.exceptionOrNull()
                    println("TrekRepository: Failed to download tiles: ${error?.message}")
                    Result.Error("Failed to download map tiles: ${error?.message}", 500)
                }
            } else {
                println("TrekRepository: Offline map manager not available, skipping tile download")
                onProgress(1.0f)
                Result.Success(true)
            }
        } catch (e: Exception) {
            println("TrekRepository: Error downloading trek offline: ${e.message}")
            Result.Error("Error downloading trek offline: ${e.message}", 500)
        }
    }

    override suspend fun removeDownloadedTrek(trekId: String): Result<Boolean> {
        return try {
            println("TrekRepository: Removing offline data for trek: $trekId")
            val result = offlineMapManager?.removeTrekTiles(trekId)
            if(result?.isSuccess == true) {
                println("TrekRepository: Offline data removed for trek: $trekId")
                fileDownloader.deleteFile(trekId)
                trekDao.deleteTrek(trekId)
                println("TrekRepository: removed trek: $trekId")
                Result.Success(true)
            } else {
                Result.Error("Unable to delete tiles", 500)
            }
        } catch (e: Exception) {
            println("TrekRepository: Error removing offline trek: ${e.message}")
            Result.Error("Error removing offline trek: ${e.message}", 500)
        }
    }

    override suspend fun isTrekFullyDownloaded(trekId: String): Boolean {
        return try {
            val hasMetadata = trekDao.getTrek(trekId) != null
            val hasCoordinates = fileDownloader.fileExists(trekId)
            val hasTiles = offlineMapManager?.isTrekDownloaded(trekId) ?: true // true if no manager (iOS)
            
            val isFullyDownloaded = hasMetadata && hasCoordinates && hasTiles
            println("TrekRepository: Trek $trekId download status - metadata: $hasMetadata, coords: $hasCoordinates, tiles: $hasTiles")
            
            isFullyDownloaded
        } catch (e: Exception) {
            println("TrekRepository: Error checking trek download status: ${e.message}")
            false
        }
    }

    override suspend fun saveNavigatorTrek(navigatorTrek: NavigatorTrek) {
        navigatorDao.insertTrek(navigatorTrek)
    }

    override suspend fun updateNavigatorTrek(id: String, points: List<Point>) {
        navigatorDao.updateNavigatorTrek(id, points)
    }

    override suspend fun getAllNavigatorTreks(): List<NavigatorTrek> {
        return navigatorDao.getAllNavigatorTreks()
    }

    override suspend fun syncNavigatorTrek() {
        try {
            val navigatorTreks = navigatorDao.getAllNavigatorTreks()
            //TODO - Sync navigator treks to server
        } catch (e: Exception) {
            println("Error syncing navigator trek: ${e.message}")
        }
    }

    override fun prepareSampleTrekCoordinates(
        currLat: Double,
        currLong: Double,
        trekId: String
    ): TrekGeoData {
        val singleLineCoordinates = mutableListOf<List<Double>>()

        val earthRadius = 6371000.0 // Earth's radius in meters
        var currentLat = currLat
        var currentLng = currLong

        // GeoJSON standard ordering: [longitude, latitude]
        singleLineCoordinates.add(listOf(currentLng, currentLat))

        var bearing = Random.nextDouble(0.0, 360.0)

        for (i in 1 until 30) {
            val distance = Random.nextDouble(5.0, 10.0)
            bearing += Random.nextDouble(-20.0, 20.0)

            val bearingRad = bearing * (PI / 180.0)
            val latRad = currentLat * (PI / 180.0)

            val deltaLat = (distance * cos(bearingRad)) / earthRadius
            val deltaLng = (distance * sin(bearingRad)) / (earthRadius * cos(latRad))

            currentLat += deltaLat * (180.0 / PI)
            currentLng += deltaLng * (180.0 / PI)

            // Store as [longitude, latitude]
            singleLineCoordinates.add(listOf(currentLng, currentLat))
        }

        return TrekGeoData(
            id = trekId,
            name = "test",
            geometry = TrekGeometry(
                type = "LineString",
                coordinates = listOf(singleLineCoordinates)
            )
        )
    }

    fun TrekGeometry.calculateBoundingBox(): List<Double> {
        val flattenedCoordinates = getFlattenedCoordinates()
        if (flattenedCoordinates.isEmpty()) return emptyList()

        var minLng = Double.MAX_VALUE
        var minLat = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE // Fixed: Do not use Double.MIN_VALUE here
        var maxLat = -Double.MAX_VALUE // Fixed: Do not use Double.MIN_VALUE here

        flattenedCoordinates.forEach { coordinate ->
            if (coordinate.size >= 2) {
                val lng = coordinate[0]
                val lat = coordinate[1]

                minLng = minOf(minLng, lng)
                maxLng = maxOf(maxLng, lng)
                minLat = minOf(minLat, lat)
                maxLat = maxOf(maxLat, lat)
            }
        }

        return listOf(minLng, minLat, maxLng, maxLat)
    }
}