package com.via.himalaya.data.repository

import com.via.himalaya.data.local.FileDownloader
import com.via.himalaya.data.local.NavigatorDao
import com.via.himalaya.data.local.TrekDao
import com.via.himalaya.data.models.NavigatorTrek
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.data.models.TrekDetailData
import com.via.himalaya.data.models.TrekPoiBundle
import com.via.himalaya.data.models.TrekSearchData
import com.via.himalaya.data.models.TreksData
import com.via.himalaya.data.models.VResponse
import com.via.himalaya.data.local.OfflineMapManager
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.domain.model.TrekGeometry
import com.via.himalaya.domain.model.Treks
import com.via.himalaya.domain.model.getFlattenedCoordinates
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.util.Constants
import com.via.himalaya.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.datetime.Instant
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
        // Mapbox groups tiles into packs with fixed zoom ranges, and you always
        // get the whole pack:
        //
        //     index  0 -> zoom 0-5    (~1.4K tiles, global)
        //     index  6 -> zoom 6-10   (~341 tiles)
        //     index 11 -> zoom 11-14  (~85 tiles)
        //     index 12 -> zoom 15-16  (~320 tiles)
        //
        // minZoom was 11, which meant zooming out even two steps from the trek's
        // default camera fell off the downloaded range and went blurry. Asking
        // for 6 costs exactly the same as asking for 8 - both pull the entire
        // 6-10 pack - so there is no reason not to take the wider range.
        //
        // Dropping to 0 would add the global 0-5 pack, which is ~1.4K raster
        // tiles for a view of the whole subcontinent nobody needs.
        private const val MIN_ZOOM = 6
        // Hard platform ceiling: Mapbox only serves offline tiles to zoom 16.
        // Beyond that the SDK upscales z16, so close-in zoom is blurry offline
        // and there is nothing we can do about it.
        private const val MAX_ZOOM = 16

        /** POIs live beside the coordinates file, which is keyed on the bare trek id. */
        private fun poiFileName(trekId: String) = "${trekId}_pois"
    }

    /**
     * Tolerant of new fields so a bundle regenerated with extra keys does not
     * crash an older build.
     */
    private val poiJson = Json { ignoreUnknownKeys = true }

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

    override suspend fun getTrekPois(
        poiUrl: String,
        trekId: String,
        poiUpdatedAt: String?
    ): Result<TrekPoiBundle> {
        val fileName = poiFileName(trekId)

        // Read the cache up front so it can still serve as a fallback if the
        // network is unreachable, even when it looks out of date.
        val cached: TrekPoiBundle? = if (fileDownloader.fileExists(fileName)) {
            try {
                fileDownloader.readFile(fileName)
                    ?.let { poiJson.decodeFromString<TrekPoiBundle>(it) }
            } catch (e: Exception) {
                println("TrekRepository: Failed to parse local POIs: ${e.message}")
                null
            }
        } else null

        if (cached != null && isSameInstant(cached.generatedAt, poiUpdatedAt)) {
            println("TrekRepository: Loaded ${cached.pois.size} POIs from local storage for trek: $trekId")
            return Result.Success(cached)
        }
        if (cached != null) {
            println("TrekRepository: Cached POIs may be stale for trek: $trekId (have ${cached.generatedAt}, want $poiUpdatedAt)")
        }

        return try {
            println("TrekRepository: Fetching POIs from network for trek: $trekId")
            val response = apiClient.get(poiUrl) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }
            if (response.status.value == 200) {
                val body = response.body<String>()
                val bundle = poiJson.decodeFromString<TrekPoiBundle>(body)
                try {
                    fileDownloader.downloadFile(poiUrl, fileName)
                    println("TrekRepository: POIs cached locally for trek: $trekId")
                } catch (e: Exception) {
                    println("TrekRepository: Failed to cache POIs: ${e.message}")
                }
                Result.Success(bundle)
            } else if (cached != null) {
                println("TrekRepository: POI fetch returned ${response.status.value}, serving cached bundle")
                Result.Success(cached)
            } else {
                Result.Error("Failed to fetch POIs: ${response.status.description}", response.status.value)
            }
        } catch (e: Exception) {
            // Offline. A stale bundle beats no bundle - the trek is being walked
            // right now and those water points are still where they were.
            if (cached != null) {
                println("TrekRepository: POI fetch failed (${e.message}), serving cached bundle for trek: $trekId")
                Result.Success(cached)
            } else {
                Result.Error("Error fetching POIs: ${e.message}", 500)
            }
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

                // POIs are small and optional - a failure here must not block the
                // download, the trail is still perfectly usable without them.
                trek.poiUrl?.let { poiUrl ->
                    val poisResult = getTrekPois(poiUrl, trek.id, trek.poiUpdatedAt)
                    if (poisResult is Result.Success) {
                        println("TrekRepository: Cached ${poisResult.data?.pois?.size ?: 0} POIs for trek: ${trek.id}")
                    } else {
                        println("TrekRepository: POI download failed, continuing: ${(poisResult as? Result.Error)?.message}")
                    }
                }

                // Fonts, sprites and the style JSON itself. Without these the
                // style cannot load offline at all and the map comes up blank,
                // however many tiles are on disk. Shared across every trek and
                // a no-op once cached, so it is cheap to repeat.
                val stylePackResult = offlineMapManager.downloadStylePack(
                    styleUri = Constants.Map.STYLE_URI,
                    onProgress = { styleProgress ->
                        onProgress(0.2f + (styleProgress * 0.1f))
                    }
                )
                if (stylePackResult.isFailure) {
                    val error = stylePackResult.exceptionOrNull()
                    println("TrekRepository: Style pack download failed: ${error?.message}")
                    return Result.Error(
                        "Failed to prepare offline map style: ${error?.message}",
                        500
                    )
                }
                onProgress(0.3f)
                println("TrekRepository: Style pack ready")

                val coordinatesJson = fileDownloader.readFile(trek.id)
                    ?: return Result.Error("Failed to read coordinates file for tile download", 500)
                val tilesResult = offlineMapManager.downloadTrekTiles(
                    trekId = trek.id,
                    coordinatesJson = coordinatesJson,
                    minZoom = MIN_ZOOM,
                    maxZoom = MAX_ZOOM,
                    onProgress = { tileProgress ->
                        onProgress(0.3f + (tileProgress * 0.6f))
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
                fileDownloader.deleteFile(poiFileName(trekId))
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
            // Tiles render nothing without the style. Builds before the style
            // fix downloaded tiles but no style pack, so this reports them as
            // incomplete and the user re-downloads into a working state.
            val hasStylePack = offlineMapManager?.isStylePackDownloaded(Constants.Map.STYLE_URI) ?: true

            val isFullyDownloaded = hasMetadata && hasCoordinates && hasTiles && hasStylePack
            println("TrekRepository: Trek $trekId download status - metadata: $hasMetadata, coords: $hasCoordinates, tiles: $hasTiles, style: $hasStylePack")
            
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
            for (navigatorTrek in navigatorTreks) {
                try {
                    val response = apiClient.post("$BASE_URL/api/navigator-trek/upload") {
                        contentType(ContentType.Application.Json)
                        headers {
                            append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                            append(HttpHeaders.Authorization, "Bearer $DEFAULT_API_KEY")
                        }
                        setBody(navigatorTrek)
                    }
                    if (response.status.value == 201) {
                        println("TrekRepository: Synced navigator trek ${navigatorTrek.id}")
                    } else {
                        println("TrekRepository: Failed to sync navigator trek ${navigatorTrek.id}: ${response.status.description}")
                    }
                } catch (e: Exception) {
                    println("TrekRepository: Error syncing navigator trek ${navigatorTrek.id}: ${e.message}")
                }
            }
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

    /**
     * Postgres serialises timestamptz as "2026-07-30T16:37:54.000Z" while the
     * generator writes "2026-07-31T15:24:11+00:00". Same kind of value, different
     * text, so compare parsed instants rather than raw strings.
     *
     * Unparseable or absent on either side counts as a match: a cached bundle we
     * cannot date is still more use than none.
     */
    private fun isSameInstant(a: String?, b: String?): Boolean {
        if (a == null || b == null || a == b) return true
        return try {
            Instant.parse(a) == Instant.parse(b)
        } catch (e: Exception) {
            println("TrekRepository: Could not compare timestamps '$a' and '$b'")
            false
        }
    }
}