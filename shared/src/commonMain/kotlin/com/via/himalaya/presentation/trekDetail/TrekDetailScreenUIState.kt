package com.via.himalaya.presentation.trekDetail

import com.via.himalaya.domain.model.TrekDetail
import com.via.himalaya.domain.model.TrekGeoData
import com.via.himalaya.util.LocationCoordinate

data class TrekDetailScreenUIState(
    val trek: TrekDetail? = null,
    val isLoading: Boolean = false,
    val errorState: String? = null,
    val geoData: TrekGeoData? = null,
    val currentLocation: LocationCoordinate? = null,
    val isNearTrekStart: Boolean = false,
    val isTrekking: Boolean = false
)
