package com.via.himalaya.domain.repo

import com.via.himalaya.domain.model.Trek
import com.via.himalaya.domain.model.TrekDetail
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.util.Result

interface TrekRepository {

    suspend fun getTreks(): Result<List<Trek>>

    suspend fun getTrek(id: String): Result<TrekDetail>
    
    suspend fun getTrekCoordinates(coordinateUrl: String): Result<TrekGeoData>

}