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

    companion object {
        private const val PAGE_SIZE = 10
    }

    private val _state = MutableStateFlow(ExploreScreenUIState())
    val state: StateFlow<ExploreScreenUIState> = _state.asStateFlow()

    init {
        loadTreks()
    }

    fun onEvent(event: ExploreScreenUIEvent) {
        when(event) {
            is ExploreScreenUIEvent.OnLoadMore -> {
                loadTreks()
            }
            is ExploreScreenUIEvent.ClearErrorMessage -> {
                _state.update { it.copy(errorState = null) }
            }
        }
    }

    private fun loadTreks() = viewModelScope.launch {
        if(state.value.hasNextPage) {
            _state.update { it.copy(isLoading = true) }
            val treks = trekRepository.getTreks(
                page = state.value.page + 1,
                limit = PAGE_SIZE,
                seed = state.value.seed
            )
            when(treks) {
                is Result.Success -> {
                    val updatedTreks = state.value.treks.toMutableList()
                    val newTreks = treks.data?.treks.orEmpty()
                    updatedTreks.addAll(newTreks)
                    _state.update {
                        it.copy(
                            page = treks.data?.page?.pageNo ?: 0,
                            isLoading = false,
                            treks = updatedTreks,
                            seed = treks.data?.page?.seed,
                            hasNextPage = treks.data?.page?.hasNext ?: false
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

}