package com.via.himalaya.presentation.trekDetail

import com.via.himalaya.domain.model.Trek
import com.via.himalaya.domain.model.TrekGeoData

data class TrekDetailScreenUIState(
    val trek: Trek? = null,
    val isLoading: Boolean = false,
    val errorState: String? = null,
    val geoData: TrekGeoData? = null
)
