package com.via.himalaya.presentation.explore

import com.via.himalaya.data.models.Trek
import com.via.himalaya.presentation.UIEffect

data class ExploreScreenUIState(
    val treks: List<Trek> = emptyList(),
    val errorState: String? = null,
    val isLoading: Boolean = false,
    val uiEffect: UIEffect? = null
)
