package com.via.himalaya.presentation.downloads

import com.via.himalaya.data.models.Trek

data class DownloadedTrekUIState(
    val isLoading: Boolean = false,
    val errorState: String? = null,
    val treks: List<Trek> = emptyList()
)
