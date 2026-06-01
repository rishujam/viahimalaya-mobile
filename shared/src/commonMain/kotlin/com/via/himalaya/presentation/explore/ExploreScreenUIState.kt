package com.via.himalaya.presentation.explore

import com.via.himalaya.data.models.Trek

data class ExploreScreenUIState(
    val treks: List<Trek> = emptyList()
)
