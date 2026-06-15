package com.via.himalaya.presentation.trekDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.via.himalaya.domain.repo.TrekRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TrekDetailViewModel(
    private val trekRepository: TrekRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrekDetailScreenUIState())
    val state: StateFlow<TrekDetailScreenUIState> = _state.asStateFlow()

    fun getTrek(trekId: String) = viewModelScope.launch {
        trekRepository.getTrek(trekId)
//        getCoordinates(trek.coordinateUrl)
    }

//    private fun getCoordinates(url: String) = viewModelScope.launch {
//        _state.update { it.copy(isLoading = true) }
//        val coordinates = trekRepository.getTrekCoordinates(url)
//        if(coordinates is Result.Success && coordinates.data != null) {
//            _state.update {
//                it.copy(
//                    isLoading = false,
//                    geoData = coordinates.data,
//                    errorState = null
//                )
//            }
//        } else {
//            _state.update {
//                it.copy(
//                    isLoading = false,
//                    errorState = coordinates.message
//                )
//            }
//        }
//    }

}