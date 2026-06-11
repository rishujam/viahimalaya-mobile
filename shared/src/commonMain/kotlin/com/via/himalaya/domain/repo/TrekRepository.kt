package com.via.himalaya.domain.repo

import com.via.himalaya.domain.model.Trek
import com.via.himalaya.util.Result

interface TrekRepository {

    suspend fun getTreks(): Result<List<Trek>>

}