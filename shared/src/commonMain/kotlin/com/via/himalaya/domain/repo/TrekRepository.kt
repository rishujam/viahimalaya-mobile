package com.via.himalaya.domain.repo

import com.via.himalaya.data.models.NavigatorTrek
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.util.Result

interface TrekRepository {

    suspend fun getTreks(): Result<List<Trek>>

    suspend fun getTrek(id: String): Result<TrekDetail>
    
    suspend fun getTrekCoordinates(coordinateUrl: String, trekId: String): Result<TrekGeoData>

    suspend fun saveTrekMetaData(meta: TrekDetail)

    suspend fun getSavedTreks(): Result<List<TrekDetail>>

    suspend fun saveNavigatorTrek(navigatorTrek: NavigatorTrek)

    suspend fun updateNavigatorTrek(id: String, points: List<Point>)

//    suspend fun deleteNavigatorTrek(id: String)

    suspend fun getAllNavigatorTreks(): List<NavigatorTrek>

//    suspend fun downloadMap(
//        trekId: String,
//        boundingBox: List<Double>,
//        onProgress: (Float) -> Unit,
//        mapStyle: String
//    ): Result<Boolean>
//
//    suspend fun removeMap(trekId: String): Result<Boolean>

}