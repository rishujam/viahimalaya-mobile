package com.via.himalaya.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.data.repository.TrekRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExploreViewModel(
    private val trekRepository: TrekRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreScreenUIState())
    val state: StateFlow<ExploreScreenUIState> = _state.asStateFlow()

    init {
        loadTreks()
    }

    private fun loadTreks() = viewModelScope.launch {
        val treks = trekRepository.getTreks()
        _state.value = _state.value.copy(
            treks = treks
        )
    }

}