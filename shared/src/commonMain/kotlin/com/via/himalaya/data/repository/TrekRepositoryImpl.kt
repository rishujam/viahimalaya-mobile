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
import com.via.himalaya.data.remote.ApiConfig
import com.via.himalaya.domain.model.RouteNetwork
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.domain.model.TrekGeometry
import com.via.himalaya.domain.model.TrekRoutes
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
    private val apiConfig: ApiConfig,
) : TrekRepository {

    companion object {
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

        /**
         * The POI file older builds saved. POIs now come inside the routes file,
         * so it is never read; deleted when a download is redone or removed.
         */
        private fun poiFileName(trekId: String) = "${trekId}_pois"

        /** The routes file, beside the POIs. */
        private fun routesFileName(trekId: String) = "${trekId}_routes"

        /**
         * The coordinate file older builds saved, under the bare trek id. Never
         * read any more; deleted when a download is redone or removed.
         */
        private fun retiredCoordinatesFileName(trekId: String) = trekId
    }

    /**
     * Tolerant of new fields so a bundle or routes file regenerated with extra
     * keys does not crash an older build.
     */
    private val lenientJson = Json { ignoreUnknownKeys = true }

    /** Serialises the upgrade of old downloads: the background job and an open trek can both start one. */
    private val upgradeLock = Mutex()

    override suspend fun getTreks(
        page: Int,
        limit: Int,
        seed: String?
    ): Result<Treks> {
        return try {
            val response = apiClient
                .get("${apiConfig.baseUrl}/api/treks") {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                        append(HttpHeaders.Authorization, "Bearer ${apiConfig.apiKey}")
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

    override suspend fun getTrekRoutes(
        routesUrl: String,
        trekId: String,
        routesUpdatedAt: String?
    ): Result<TrekRoutes> = getCachedJson(
        what = "routes",
        url = routesUrl,
        fileName = routesFileName(trekId),
        wantedVersion = routesUpdatedAt,
        versionOf = { it.generatedAt },
        decode = { lenientJson.decodeFromString<TrekRoutes>(it) }
    )

    /**
     * A JSON file from R2, kept on disk under [fileName]. The R2 key never changes,
     * so the copy on disk is used while its own version ([versionOf]) matches
     * [wantedVersion], the one the treks table carries. Otherwise it is fetched
     * again - and when that fails, offline say, the old copy is served anyway: the
     * trek is being walked right now and the trail has not moved.
     */
    private suspend fun <T> getCachedJson(
        what: String,
        url: String,
        fileName: String,
        wantedVersion: String?,
        versionOf: (T) -> String?,
        decode: (String) -> T
    ): Result<T> {
        // Read the cache up front so it can still serve as a fallback if the
        // network is unreachable, even when it looks out of date.
        val cached: T? = if (fileDownloader.fileExists(fileName)) {
            try {
                fileDownloader.readFile(fileName)?.let(decode)
            } catch (e: Exception) {
                println("TrekRepository: Failed to parse local $what ($fileName): ${e.message}")
                null
            }
        } else null

        if (cached != null && isSameInstant(versionOf(cached), wantedVersion)) {
            println("TrekRepository: Loaded $what from local storage ($fileName)")
            return Result.Success(cached)
        }
        if (cached != null) {
            println("TrekRepository: Cached $what may be stale ($fileName): have ${versionOf(cached)}, want $wantedVersion")
        }

        return try {
            println("TrekRepository: Fetching $what from network ($fileName)")
            val response = apiClient.get(url) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                }
            }
            if (response.status.value == 200) {
                val text = response.body<String>()
                val data = decode(text)
                // The text just fetched is what goes on disk - decoded first, so
                // a broken file never replaces a good copy.
                if (fileDownloader.writeFile(fileName, text)) {
                    println("TrekRepository: $what cached locally ($fileName)")
                } else {
                    println("TrekRepository: Failed to cache $what ($fileName)")
                }
                Result.Success(data)
            } else if (cached != null) {
                println("TrekRepository: $what fetch returned ${response.status.value}, serving cached copy")
                Result.Success(cached)
            } else {
                Result.Error("Failed to fetch $what: ${response.status.description}", response.status.value)
            }
        } catch (e: Exception) {
            if (cached != null) {
                println("TrekRepository: $what fetch failed (${e.message}), serving cached copy ($fileName)")
                Result.Success(cached)
            } else {
                Result.Error("Error fetching $what: ${e.message}", 500)
            }
        }
    }

    override suspend fun searchTreks(query: String): Result<List<Trek>> {
        return try {
            val response = apiClient.get("${apiConfig.baseUrl}/api/treks/search") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer ${apiConfig.apiKey}")
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

    /**
     * A downloaded trek is read from Room, unless it was saved before routes
     * existed: then the API's fresh copy is returned, so the screen can draw it
     * while the download itself is redone. Offline, the old row is returned and
     * the screen asks for a connection (its routesUrl is null).
     */
    override suspend fun getTrek(id: String): Result<TrekDetail> {
        val local = try {
            trekDao.getTrek(id)
        } catch (e: Exception) {
            null
        }
        if (local != null && local.routesUrl != null) return Result.Success(local)
        val remote = fetchTrek(id)
        return if (remote is Result.Success || local == null) remote else Result.Success(local)
    }

    private suspend fun fetchTrek(id: String): Result<TrekDetail> {
        return try {
            val response = apiClient.get("${apiConfig.baseUrl}/api/treks/$id") {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.Authorization, "Bearer ${apiConfig.apiKey}")
                }
            }
            when (response.status.value) {
                200 -> Result.Success(response.body<VResponse<TrekDetailData>>().data.toTrekDetail())
                404 -> Result.Error("Trek not found", 404)
                else -> Result.Error(response.status.description, response.status.value)
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
                val routesUrl = trek.routesUrl
                    ?: return Result.Error("This trek has no routes file yet", 500)
                val routesResult = getTrekRoutes(routesUrl, trek.id, trek.routesUpdatedAt)
                val routes = (routesResult as? Result.Success)?.data
                    ?: return Result.Error(
                        "Failed to download routes: ${(routesResult as? Result.Error)?.message}",
                        500
                    )
                onProgress(0.2f)
                println("TrekRepository: Trek routes downloaded")

                // POIs come inside the routes file, so there is nothing else to fetch.

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

                // Tiles for every section, not just the default: a trekker who
                // switches to an alternative offline still has its map.
                val tilesResult = offlineMapManager.downloadTrekTiles(
                    trekId = trek.id,
                    coordinatesJson = routesGeoJson(routes),
                    minZoom = MIN_ZOOM,
                    maxZoom = MAX_ZOOM,
                    onProgress = { tileProgress ->
                        onProgress(0.3f + (tileProgress * 0.6f))
                    }
                )
                if (tilesResult.isSuccess) {
                    onProgress(0.9f)
                    trekDao.insert(trek)
                    fileDownloader.deleteFile(retiredCoordinatesFileName(trek.id))
                    fileDownloader.deleteFile(poiFileName(trek.id))
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
                fileDownloader.deleteFile(retiredCoordinatesFileName(trekId))
                fileDownloader.deleteFile(poiFileName(trekId))
                fileDownloader.deleteFile(routesFileName(trekId))
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
            // A row without routesUrl is a download from before routes: it still
            // counts as downloaded (it is being upgraded), but has no routes file.
            val hasMetadata = trekDao.getTrek(trekId) != null
            val hasCoordinates = fileDownloader.fileExists(routesFileName(trekId))
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

    override suspend fun refreshDownloadedTrek(trekId: String): Result<TrekDetail?> = upgradeLock.withLock {
        val local = trekDao.getTrek(trekId)
            ?: return@withLock Result.Success(null)                 // not downloaded

        val fresh = fetchTrek(trekId)
        val trek = (fresh as? Result.Success)?.data
            ?: return@withLock Result.Error(fresh.message ?: "Could not fetch the trek", fresh.code ?: 500)
        if (trek.routesUrl == null) {
            return@withLock Result.Error("The trek has no routes file on the server yet", 404)
        }
        val current = local.routesUrl != null && isSameInstant(local.routesUpdatedAt, trek.routesUpdatedAt)
        if (current) return@withLock Result.Success(null)

        println("TrekRepository: Refreshing download of $trekId (have ${local.routesUpdatedAt}, server ${trek.routesUpdatedAt})")
        when (val result = downloadTrekOffline(trek) { }) {
            is Result.Success -> Result.Success(trek)
            else -> Result.Error(result.message ?: "Could not refresh $trekId", result.code ?: 500)
        }
    }

    override suspend fun refreshDownloadedTreks(): Boolean {
        val downloads = try {
            trekDao.getTreks().orEmpty()
        } catch (e: Exception) {
            return false
        }
        var allDone = true
        downloads.forEach { if (refreshDownloadedTrek(it.id) is Result.Error) allDone = false }
        return allDone
    }

    /**
     * Every section as one MultiLineString, in the shape the tile download reads
     * (a feature with a geometry), so the tiles cover alternatives and side trips.
     */
    private fun routesGeoJson(routes: TrekRoutes): String = buildJsonObject {
        putJsonObject("geometry") {
            put("type", "MultiLineString")
            put("coordinates", JsonArray(RouteNetwork(routes).allLines().map { line ->
                JsonArray(line.map { p -> JsonArray(p.map { JsonPrimitive(it) }) })
            }))
        }
    }.toString()

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
                    val response = apiClient.post("${apiConfig.baseUrl}/api/navigator-trek/upload") {
                        contentType(ContentType.Application.Json)
                        headers {
                            append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                            append(HttpHeaders.Authorization, "Bearer ${apiConfig.apiKey}")
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