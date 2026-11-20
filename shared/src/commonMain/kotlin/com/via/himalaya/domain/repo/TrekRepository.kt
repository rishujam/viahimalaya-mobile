package com.via.himalaya.domain.repo

import com.via.himalaya.data.models.NavigatorTrek
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.domain.model.TrekRoutes
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
    
    /**
     * A trek's routes file, cached on disk. Like the POI bundle, the R2 key never
     * changes, so a regenerated file is only picked up when [routesUpdatedAt]
     * differs from the version stored. Offline, a stale copy is served.
     */
    suspend fun getTrekRoutes(
        routesUrl: String,
        trekId: String,
        routesUpdatedAt: String?
    ): Result<TrekRoutes>

    suspend fun getDownloadedTreks(): Result<List<TrekDetail>>

    suspend fun downloadTrekOffline(
        trek: TrekDetail,
        onProgress: (Float) -> Unit
    ): Result<Boolean>

    suspend fun removeDownloadedTrek(trekId: String): Result<Boolean>

    suspend fun isTrekFullyDownloaded(trekId: String): Boolean

    /**
     * Brings a downloaded trek up to date with the server. A download is read from
     * the phone, never the API, so without this it would keep the routes file it
     * was saved with forever - a trek rebuilt later (Goechala with its Dzongri
     * path) would never reach it.
     *
     * Redone when the download is from before routes (no routesUrl) or the
     * server's routes file is newer: fresh details, the routes file, and tiles for
     * every section; old files are deleted. Success(the new details) when redone,
     * Success(null) when there was nothing to do (not downloaded, or current);
     * an error - offline, say - leaves the download as it was.
     */
    suspend fun refreshDownloadedTrek(trekId: String): Result<TrekDetail?>

    /** [refreshDownloadedTrek] for every download. True when none failed. */
    suspend fun refreshDownloadedTreks(): Boolean

    suspend fun saveNavigatorTrek(navigatorTrek: NavigatorTrek)

    suspend fun updateNavigatorTrek(id: String, points: List<Point>)

    suspend fun syncNavigatorTrek()

    suspend fun getAllNavigatorTreks(): List<NavigatorTrek>

    fun prepareSampleTrekCoordinates(
        currLat: Double,
        currLong: Double,
        trekId: String
    ): TrekGeoData

    //    suspend fun deleteNavigatorTrek(id: String)

}