package com.via.himalaya.domain.repo

import com.via.himalaya.data.models.NavigatorTrek
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.domain.model.Treks
import com.via.himalaya.util.Result

interface TrekRepository {

    suspend fun getTreks(
        page: Int,
        limit: Int,
        seed: String? = null
    ): Result<Treks>

    suspend fun getTrek(id: String): Result<TrekDetail>
    
    suspend fun searchTreks(query: String): Result<List<Trek>>
    
    suspend fun getTrekCoordinates(coordinateUrl: String, trekId: String): Result<TrekGeoData>

    suspend fun saveTrekMetaData(meta: TrekDetail)

    suspend fun getSavedTreks(): Result<List<TrekDetail>>

    suspend fun saveNavigatorTrek(navigatorTrek: NavigatorTrek)

    suspend fun updateNavigatorTrek(id: String, points: List<Point>)

    suspend fun syncNavigatorTrek()

//    suspend fun deleteNavigatorTrek(id: String)

    suspend fun getAllNavigatorTreks(): List<NavigatorTrek>

    /**
     * Downloads trek for complete offline use including metadata, coordinates, and map tiles.
     *
     * @param trekId The trek identifier
     * @param onProgress Progress callback (0.0 to 1.0) for the entire download process
     * @return Result indicating success or failure
     */
    suspend fun downloadTrekOffline(
        trekId: String,
        onProgress: (Float) -> Unit
    ): Result<Boolean>

    /**
     * Removes offline trek data including metadata, coordinates, and map tiles.
     *
     * @param trekId The trek identifier
     * @return Result indicating success or failure
     */
    suspend fun removeTrekOffline(trekId: String): Result<Boolean>

    /**
     * Checks if trek is fully downloaded (metadata + coordinates + tiles).
     *
     * @param trekId The trek identifier
     * @return true if all components are downloaded, false otherwise
     */
    suspend fun isTrekFullyDownloaded(trekId: String): Boolean

    /**
     * Gets the size of downloaded map tiles for a trek in bytes.
     *
     * @param trekId The trek identifier
     * @return Size in bytes, or 0 if not downloaded
     */
    suspend fun getTrekTileSize(trekId: String): Long

}