package com.via.himalaya.presentation.trekDetail

import com.via.himalaya.domain.model.TrekDetail
import com.via.himalaya.domain.model.TrekGeoData

data class TrekDetailScreenUIState(
    val trek: TrekDetail? = null,
    val isLoading: Boolean = false,
    val errorState: String? = null,
    val geoData: TrekGeoData? = null
)
