package com.via.himalaya.domain

import com.via.himalaya.data.models.Loc
import kotlinx.coroutines.flow.Flow

interface LocationEmitter {

    suspend fun getLocation(): Loc?

    fun getLiveLocationStream(): Flow<Loc>

}