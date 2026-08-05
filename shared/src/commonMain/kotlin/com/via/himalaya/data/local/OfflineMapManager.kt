package com.via.himalaya.data.local

/**
 * Manages offline map tiles for trek navigation.
 *
 * This manager handles downloading and managing map tiles from Mapbox
 * for offline use. It works alongside the existing trek download system
 * to provide complete offline navigation capability.
 *
 * Components downloaded:
 * 1. Style Pack - Fonts, sprites, style JSON (shared across all treks)
 * 2. Tile Region - Actual map tiles for specific geographic area (per trek)
 */
interface OfflineMapManager {
    /**
     * Downloads style pack for offline use (fonts, sprites, style JSON).
     * This should be called once when the app initializes.
     * The style pack is shared across all treks.
     *
     * @param styleUri The Mapbox style URI (e.g., "mapbox://styles/mapbox/standard")
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Result indicating success or failure
     */
    suspend fun downloadStylePack(
        styleUri: String,
        onProgress: (Float) -> Unit
    ): Result<Boolean>

    /**
     * Downloads map tiles for a specific trek area.
     *
     * This calculates the bounding box from the trek's GeoJSON coordinates,
     * adds a buffer for context, and downloads all tiles within that area
     * for the specified zoom range.
     *
     * @param trekId Unique identifier for the trek (used as tile region ID)
     * @param coordinatesJson Trek coordinates in GeoJSON format
     * @param minZoom Minimum zoom level (default: 11 for overview)
     * @param maxZoom Maximum zoom level (default: 15 for detail)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Result indicating success or failure
     */
    suspend fun downloadTrekTiles(
        trekId: String,
        coordinatesJson: String,
        minZoom: Int = 11,
        maxZoom: Int = 15,
        onProgress: (Float) -> Unit
    ): Result<Boolean>

    /**
     * Removes offline tiles for a specific trek.
     *
     * @param trekId The trek identifier
     * @return Result indicating success or failure
     */
    suspend fun removeTrekTiles(trekId: String): Result<Boolean>

    /**
     * Checks if tiles are downloaded for a trek.
     *
     * @param trekId The trek identifier
     * @return true if tiles are downloaded, false otherwise
     */
    suspend fun isTrekDownloaded(trekId: String): Boolean

    /**
     * Whether the style pack for [styleUri] is cached.
     *
     * Tiles alone are not enough - without the style JSON, fonts and sprites the
     * map cannot render offline at all. Treated as part of "is this trek
     * downloaded", so builds that shipped without a style pack report themselves
     * as incomplete and re-download instead of failing silently on the trail.
     */
    suspend fun isStylePackDownloaded(styleUri: String): Boolean

    /**
     * Gets the size of downloaded tiles in bytes.
     *
     * @param trekId The trek identifier
     * @return Size in bytes, or 0 if not downloaded
     */
    suspend fun getTrekTileSize(trekId: String): Long

    /**
     * Gets all downloaded tile regions.
     *
     * @return List of trek IDs that have tiles downloaded
     */
//    suspend fun getAllDownloadedTreks(): List<String>
}