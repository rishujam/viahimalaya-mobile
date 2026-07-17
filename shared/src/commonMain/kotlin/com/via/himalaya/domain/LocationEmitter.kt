package com.via.himalaya.domain

import com.via.himalaya.data.models.Loc
import com.via.himalaya.domain.model.LocationResponse
import kotlinx.coroutines.flow.Flow

interface LocationEmitter {

    fun getLocation(
        locationCallback: (LocationResponse) -> Unit
    )

    fun getLiveLocationStream(): Flow<Loc>

}
