package com.via.himalaya.presentation.explore

import com.via.himalaya.data.models.Trek

data class ExploreScreenUIState(
    val treks: List<Trek> = emptyList(),
    val tempTreks: List<Trek> = emptyList(),
    val errorToast: String? = null,
    val messageDisplay: String? = null,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val page: Int = 0,
    val seed: String? = null,
    val hasNextPage: Boolean = true
)
