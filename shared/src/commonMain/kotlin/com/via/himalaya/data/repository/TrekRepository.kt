package com.via.himalaya.data.repository

import com.via.himalaya.data.models.Trek

interface TrekRepository {

    suspend fun getTreks(): List<Trek>

}