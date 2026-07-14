package com.via.himalaya.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.util.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadedTrekViewModel(
    private val trekRepository: TrekRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadedTrekUIState())
    val state: StateFlow<DownloadedTrekUIState> = _state.asStateFlow()

    init {
        getDownloadedTreks()
    }

    private fun getDownloadedTreks() = viewModelScope.launch {
        _state.update {
            it.copy(
                isLoading = true
            )
        }
        val result = trekRepository.getDownloadedTreks()
        if(result is Result.Success) {
            val treks = result.data?.map {
                it.toTrek()
            }
            treks?.let {
                _state.update {
                    it.copy(
                        isLoading = false,
                        treks = treks,
                        errorState = null
                    )
                }
            } ?: run {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorState = result.message
                    )
                }
            }
        } else {
            _state.update {
                it.copy(
                    isLoading = false,
                    errorState = result.message
                )
            }
        }
    }

}