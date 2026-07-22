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
    
    suspend fun getTrekCoordinates(
        coordinateUrl: String,
        trekId: String
    ): Result<TrekGeoData>

    suspend fun getDownloadedTreks(): Result<List<TrekDetail>>

    suspend fun downloadTrekOffline(
        trek: TrekDetail,
        onProgress: (Float) -> Unit
    ): Result<Boolean>

    suspend fun removeDownloadedTrek(trekId: String): Result<Boolean>

    suspend fun isTrekFullyDownloaded(trekId: String): Boolean

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