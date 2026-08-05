package com.via.himalaya.data.local

import android.util.Log
import com.mapbox.bindgen.Value
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import com.via.himalaya.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume

/**
 * Android implementation of OfflineMapManager using Mapbox Maps SDK.
 * 
 * This class handles downloading and managing offline map tiles for trek navigation.
 * It uses Mapbox's TileStore and OfflineManager APIs to download style packs and tile regions.
 */
class AndroidOfflineMapManager(
    private val tileStore: TileStore
) : OfflineMapManager {

    /**
     * Mapbox's OfflineManager is thread-affine - it must be called from the
     * thread that created it, and calling it elsewhere fails silently: no
     * exception, no callback, the coroutine just hangs.
     *
     * Creating it lazily on the first Main-dispatched call, and only ever
     * touching it from Main, keeps creation and use on the same thread no
     * matter which thread Koin happened to resolve this object on.
     *
     * TileStore has no such constraint.
     */
    private val offlineManager: OfflineManager by lazy { OfflineManager() }
    
    companion object {
        private const val TAG = "OfflineMapManager"
        private const val STYLE_PACK_METADATA_KEY = "viahimalaya-style-pack"
        // Buffer around trek path: 0.002 degrees ≈ 220 meters on each side
        // This provides enough coverage for GPS drift and nearby landmarks
        // without downloading excessive area
        private const val BUFFER_DEGREES = 0.002
    }

    override suspend fun downloadStylePack(
        styleUri: String,
        onProgress: (Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
        try {
            val stylePackOptions = StylePackLoadOptions.Builder()
                .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                .metadata(Value(STYLE_PACK_METADATA_KEY))
                .acceptExpired(false)
                .build()

            val cancelable = offlineManager.loadStylePack(
                styleUri,
                stylePackOptions,
                { progress ->
                    val total = maxOf(progress.requiredResourceCount, 1)
                    val progressVal = progress.completedResourceCount.toFloat() / total.toFloat()
                    onProgress(progressVal)
                }
            ) { expected ->
                if (expected.isValue) {
                    val stylePack = expected.value
                    Log.d(TAG, "Style pack downloaded successfully: ${stylePack?.styleURI}")
                    continuation.resume(Result.success(true))
                } else {
                    val error = expected.error
                    Log.e(TAG, "Style pack download failed: $error")
                    continuation.resume(Result.failure(Exception(error?.toString() ?: "Unknown error")))
                }
            }

            continuation.invokeOnCancellation {
                cancelable.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading style pack", e)
            continuation.resume(Result.failure(e))
        }
        }
    }

    override suspend fun downloadTrekTiles(
        trekId: String,
        coordinatesJson: String,
        minZoom: Int,
        maxZoom: Int,
        onProgress: (Float) -> Unit
    ): Result<Boolean> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
        try {
            // 1. Parse GeoJSON and calculate bounding box
            val boundingBox = calculateBoundingBoxFromGeoJson(coordinatesJson)
            if (boundingBox == null) {
                continuation.resume(Result.failure(Exception("Failed to calculate bounding box from coordinates")))
                return@suspendCancellableCoroutine
            }
            
            Log.d(TAG, "Calculated bounding box for $trekId: $boundingBox")
            
            // 2. Create polygon from bounding box
            val polygon = createPolygonFromBoundingBox(boundingBox)
            
            // 3. Create tileset descriptor
            val tilesetDescriptorOptions = TilesetDescriptorOptions.Builder()
                .styleURI(Constants.Map.STYLE_URI)
                .minZoom(minZoom.toByte())
                .maxZoom(maxZoom.toByte())
                .build()
            
            val tilesetDescriptor = offlineManager.createTilesetDescriptor(
                tilesetDescriptorOptions
            )
            
            // 4. Create tile region load options
            val loadOptions = TileRegionLoadOptions.Builder()
                .geometry(polygon)
                .descriptors(listOf(tilesetDescriptor))
                .metadata(Value(trekId))
                .acceptExpired(false)
                .build()
            
            // 5. Download tile region
            val cancelable = tileStore.loadTileRegion(
                trekId,
                loadOptions,
                { progress ->
                    val total = maxOf(progress.requiredResourceCount, 1)
                    val progressVal = progress.completedResourceCount.toFloat() / total.toFloat()
                    onProgress(progressVal)
                    Log.d(TAG, "Trek $trekId download progress: ${(progressVal * 100).toInt()}%")
                }
            ) { expected ->
                if (expected.isValue) {
                    val tileRegion = expected.value
                    val sizeMB = (tileRegion?.completedResourceSize ?: 0) / (1024.0 * 1024.0)
                    Log.d(TAG, "Tile region downloaded for $trekId: ${"%.2f".format(sizeMB)} MB")
                    continuation.resume(Result.success(true))
                } else {
                    val error = expected.error
                    Log.e(TAG, "Tile region download failed for $trekId: $error")
                    continuation.resume(Result.failure(Exception(error?.toString() ?: "Unknown error")))
                }
            }

            continuation.invokeOnCancellation {
                cancelable.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading trek tiles for $trekId", e)
            continuation.resume(Result.failure(e))
        }
        }
    }

    override suspend fun isStylePackDownloaded(styleUri: String): Boolean =
        withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            try {
                offlineManager.getStylePack(styleUri) { expected ->
                    val present = expected.isValue && expected.value != null
                    Log.d(TAG, "Style pack present for $styleUri: $present")
                    continuation.resume(present)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking style pack", e)
                continuation.resume(false)
            }
        }
    }

    override suspend fun removeTrekTiles(trekId: String): Result<Boolean> =
        suspendCancellableCoroutine { continuation ->
            try {
                tileStore.removeTileRegion(trekId) { expected ->
                    if (expected.isValue) {
                        Log.d(TAG, "Tile region removed: $trekId")
                        continuation.resume(Result.success(true))
                    } else {
                        val error = expected.error
                        Log.e(TAG, "Failed to remove tile region $trekId: $error")
                        continuation.resume(Result.failure(Exception(error?.toString() ?: "Unknown error")))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing trek tiles for $trekId", e)
                continuation.resume(Result.failure(e))
            }
        }

    override suspend fun isTrekDownloaded(trekId: String): Boolean = 
        suspendCancellableCoroutine { continuation ->
            tileStore.getTileRegion(trekId) { expected ->
                continuation.resume(expected.isValue && expected.value != null)
            }
        }

    override suspend fun getTrekTileSize(trekId: String): Long = 
        suspendCancellableCoroutine { continuation ->
            tileStore.getTileRegion(trekId) { expected ->
                if (expected.isValue) {
                    val tileRegion = expected.value
                    continuation.resume(tileRegion?.completedResourceSize ?: 0L)
                } else {
                    continuation.resume(0L)
                }
            }
        }

//    override suspend fun getAllDownloadedTreks(): List<String> =
//        suspendCancellableCoroutine { continuation ->
//            tileStore.getAllTileRegions { expected ->
//                if (expected.isValue) {
//                    val tileRegions = expected.value ?: emptyList()
//                    val trekIds = tileRegions.mapNotNull { region ->
//                        // Extract trek ID from metadata
//                        region.metadata?.let { metadata ->
//                            when {
//                                metadata.isString -> metadata.contents as? String
//                                else -> null
//                            }
//                        }
//                    }
//                    continuation.resume(trekIds)
//                } else {
//                    val error = expected.error
//                    Log.e(TAG, "Failed to get all tile regions: $error")
//                    continuation.resume(emptyList())
//                }
//            }
//        }

    /**
     * Calculate bounding box from GeoJSON coordinates.
     * Returns [minLng, minLat, maxLng, maxLat] with buffer, or null if parsing fails.
     */
    private fun calculateBoundingBoxFromGeoJson(geoJsonString: String): List<Double>? {
        return try {
            val json = Json.parseToJsonElement(geoJsonString).jsonObject
            val geometry = json["geometry"]?.jsonObject ?: return null
            val type = geometry["type"]?.jsonPrimitive?.content ?: return null
            val coordinates = geometry["coordinates"]?.jsonArray ?: return null

            var minLng = Double.MAX_VALUE
            var minLat = Double.MAX_VALUE
            var maxLng = -Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE

            when (type) {
                "LineString" -> {
                    // coordinates is array of [lng, lat] pairs
                    coordinates.forEach { point ->
                        val pointArray = point.jsonArray
                        if (pointArray.size >= 2) {
                            val lng = pointArray[0].jsonPrimitive.content.toDouble()
                            val lat = pointArray[1].jsonPrimitive.content.toDouble()
                            minLng = minOf(minLng, lng)
                            minLat = minOf(minLat, lat)
                            maxLng = maxOf(maxLng, lng)
                            maxLat = maxOf(maxLat, lat)
                        }
                    }
                }
                "MultiLineString" -> {
                    // coordinates is array of arrays of [lng, lat] pairs
                    coordinates.forEach { line ->
                        line.jsonArray.forEach { point ->
                            val pointArray = point.jsonArray
                            if (pointArray.size >= 2) {
                                val lng = pointArray[0].jsonPrimitive.content.toDouble()
                                val lat = pointArray[1].jsonPrimitive.content.toDouble()
                                minLng = minOf(minLng, lng)
                                minLat = minOf(minLat, lat)
                                maxLng = maxOf(maxLng, lng)
                                maxLat = maxOf(maxLat, lat)
                            }
                        }
                    }
                }
                else -> {
                    Log.w(TAG, "Unsupported geometry type: $type")
                    return null
                }
            }

            // Add buffer (approximately 1km on each side)
            listOf(
                minLng - BUFFER_DEGREES,
                minLat - BUFFER_DEGREES,
                maxLng + BUFFER_DEGREES,
                maxLat + BUFFER_DEGREES
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GeoJSON", e)
            null
        }
    }

    /**
     * Create a polygon from bounding box coordinates.
     * @param boundingBox [minLng, minLat, maxLng, maxLat]
     */
    private fun createPolygonFromBoundingBox(boundingBox: List<Double>): Polygon {
        val coords = listOf(
            Point.fromLngLat(boundingBox[0], boundingBox[1]), // SW
            Point.fromLngLat(boundingBox[2], boundingBox[1]), // SE
            Point.fromLngLat(boundingBox[2], boundingBox[3]), // NE
            Point.fromLngLat(boundingBox[0], boundingBox[3]), // NW
            Point.fromLngLat(boundingBox[0], boundingBox[1])  // Close polygon
        )
        return Polygon.fromLngLats(listOf(coords))
    }
}
