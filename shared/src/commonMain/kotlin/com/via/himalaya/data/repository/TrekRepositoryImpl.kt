package com.via.himalaya.data.repository

import com.via.himalaya.data.local.FileDownloader
import com.via.himalaya.data.local.TrekDao
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.data.models.TrekDetailData
import com.via.himalaya.data.models.TreksData
import com.via.himalaya.data.models.VResponse
import com.via.himalaya.domain.model.TrekGeoData
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

class TrekRepositoryImpl(
    private val apiClient: HttpClient,
    private val trekDao: TrekDao,
    private val fileDownloader: FileDownloader
) : TrekRepository {

    companion object {
        private const val BASE_URL = "https://viahimalaya.com"
        const val DEFAULT_API_KEY = "ea6265827acc4132d98dc8e37727f36fda3b91e9c4c6d79b4cb5b6c89d9fa6cf"
//        private const val MIN_ZOOM = 11
//        private const val MAX_ZOOM = 15
    }

    override suspend fun getTreks(): Result<List<Trek>> {
        return try {
            val response = apiClient
                .get("$BASE_URL/api/treks") {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                        append(HttpHeaders.Authorization, "Bearer $DEFAULT_API_KEY")
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

    override suspend fun getTrekCoordinates(coordinateUrl: String, trekId: String): Result<TrekGeoData> {
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

    override suspend fun saveTrekMetaData(meta: TrekDetail) {
        try {
            trekDao.insert(meta)
        } catch (e: Exception) {
            println("error inserting trek meta: ${e.message}")
        }
    }

    override suspend fun getSavedTreks(): Result<List<TrekDetail>> {
        try {
            val treks = trekDao.getTreks()
            return if(!treks.isNullOrEmpty()) {
                Result.Success(treks)
            } else {
                Result.Error("No treks found", 204)
            }
        } catch (e: Exception) {
            return Result.Error("Error Loading Treks: ${e.message}", 400)
        }
    }

//    override suspend fun downloadMap(
//        trekId: String,
//        boundingBox: List<Double>,
//        onProgress: (Float) -> Unit,
//        mapStyle: String
//    ): Result<Boolean> = withContext(Dispatchers.IO) {
//        try {
//            downloadStylePackIfNeeded(mapStyle)
//
//            // Step 2: Download trek coordinates
//            val coordsDownloaded = downloadCoordinatesFileToLocal(
//                trek.coordinateUrl,
//                trekId
//            )
//            if (!coordsDownloaded) {
//                return@withContext Result.Error("Failed to download coordinates", 500)
//            }
//
//            // Step 3: Download map tiles for the trek area
//            val tileRegionDownloaded = downloadTileRegion(
//                trekId,
//                boundingBox,
//                onProgress
//            )
//
//            if (tileRegionDownloaded) {
//                Result.Success(true)
//            } else {
//                Result.Error("Failed to download tile region", 500)
//            }
//        } catch (e: Exception) {
//            Result.Error("Error downloading trek offline: ${e.message}", 500)
//        }
//    }
//
//    private suspend fun downloadStylePackIfNeeded(mapStyle: String) {
//        // Check if style pack already exists
//        val stylePacks = offlineManager.getAllStylePacks()
//        val hasStylePack = stylePacks.value?.any {
//            it.styleURI == mapStyle
//        } ?: false
//
//        if (!hasStylePack) {
//            val stylePackOptions = StylePackLoadOptions.Builder()
//                .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
//                .acceptExpired(false)
//                .build()
//
//            suspendCoroutine<Unit> { continuation ->
//                offlineManager.loadStylePack(
//                    STYLE_URI,
//                    stylePackOptions,
//                    { progress -> /* Track progress if needed */ },
//                    { expected ->
//                        expected.fold(
//                            { continuation.resume(Unit) },
//                            { continuation.resumeWithException(Exception(it.message)) }
//                        )
//                    }
//                )
//            }
//        }
//    }
//
//    private suspend fun downloadTileRegion(
//        trekId: String,
//        boundingBox: List<Double>,
//        onProgress: (Float) -> Unit
//    ): Boolean = suspendCoroutine { continuation ->
//        // Create tileset descriptor
//        val tilesetDescriptorOptions = TilesetDescriptorOptions.Builder()
//            .styleURI(STYLE_URI)
//            .minZoom(MIN_ZOOM.toByte())
//            .maxZoom(MAX_ZOOM.toByte())
//            .build()
//
//        val tilesetDescriptor = offlineManager.createTilesetDescriptor(
//            tilesetDescriptorOptions
//        )
//
//        // Convert bounding box to polygon
//        val polygon = createPolygonFromBoundingBox(boundingBox)
//
//        // Create tile region load options
//        val loadOptions = TileRegionLoadOptions.Builder()
//            .geometry(polygon)
//            .descriptors(listOf(tilesetDescriptor))
//            .metadata(hashMapOf(
//                "trekId" to Value.valueOf(trekId),
//                "downloadDate" to Value.valueOf(System.currentTimeMillis())
//            ))
//            .acceptExpired(false)
//            .build()
//
//        // Download tile region
//        tileStore.loadTileRegion(
//            trekId, // Use trekId as region ID
//            loadOptions,
//            { progress ->
//                val total = maxOf(progress.requiredResourceCount, 1)
//                val progressVal = progress.completedResourceCount.toFloat() / total.toFloat()
//                onProgress(progressVal)
//            }
//        ) { expected ->
//            expected.fold(
//                { continuation.resume(true) },
//                { continuation.resume(false) }
//            )
//        }
//    }
//
//    private fun createPolygonFromBoundingBox(boundingBox: List<Double>): Polygon {
//        // boundingBox format: [minLng, minLat, maxLng, maxLat]
//        val coords = listOf(
//            Point.fromLngLat(boundingBox[0], boundingBox[1]), // SW
//            Point.fromLngLat(boundingBox[2], boundingBox[1]), // SE
//            Point.fromLngLat(boundingBox[2], boundingBox[3]), // NE
//            Point.fromLngLat(boundingBox[0], boundingBox[3]), // NW
//            Point.fromLngLat(boundingBox[0], boundingBox[1])  // Close polygon
//        )
//        return Polygon.fromLngLats(listOf(coords))
//    }
//
//    override suspend fun removeTrekOffline(trekId: String): Result<Boolean> {
//        return try {
//            tileStore.removeTileRegion(trekId)
//            fileDownloader.deleteFile(trekId)
//            Result.Success(true)
//        } catch (e: Exception) {
//            Result.Error("Error removing offline trek: ${e.message}", 500)
//        }
//    }
}