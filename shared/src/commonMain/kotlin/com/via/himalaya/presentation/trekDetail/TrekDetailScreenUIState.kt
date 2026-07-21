package com.via.himalaya.presentation.trekDetail

import com.via.himalaya.data.models.Loc
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.domain.model.LocationResponse
import com.via.himalaya.domain.model.TrekGeoData

data class TrekDetailScreenUIState(
    val trek: TrekDetail? = null,
    val isLoading: Boolean = false,
    val errorToast: String? = null,
    val messageDisplay: String? = null,
    val geoData: TrekGeoData? = null,
    val initialLocation: LocationResponse? = null,
    val liveLocation: Loc? = null,
    val isNearTrekStart: Boolean = false,
    val isTrekking: Boolean = false,
    val userEmail: String? = null
)
