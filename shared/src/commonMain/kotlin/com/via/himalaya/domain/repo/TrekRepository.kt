package com.via.himalaya.domain.repo

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

//    suspend fun downloadMap(
//        trekId: String,
//        boundingBox: List<Double>,
//        onProgress: (Float) -> Unit,
//        mapStyle: String
//    ): Result<Boolean>
//
//    suspend fun removeMap(trekId: String): Result<Boolean>

}