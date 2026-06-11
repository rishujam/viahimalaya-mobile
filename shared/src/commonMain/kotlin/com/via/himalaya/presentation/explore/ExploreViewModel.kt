package com.via.himalaya.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        _state.update { it.copy(isLoading = true) }
        when(val treks = trekRepository.getTreks()) {
            is Result.Success -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        treks = treks.data.orEmpty()
                    )
                }
            }
            is Result.Error -> {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorState = treks.message
                    )
                }
            }
            is Result.Loading -> {
                _state.update {
                    it.copy(
                        isLoading = true
                    )
                }
            }
        }
    }

}