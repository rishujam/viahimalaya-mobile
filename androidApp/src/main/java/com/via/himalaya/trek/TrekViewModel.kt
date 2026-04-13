package com.via.himalaya.trek

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.via.himalaya.data.repository.TrekRepository

class TrekViewModel(
    private val trekRepository: TrekRepository
) : ViewModel() {

    var state by mutableStateOf(TrekScreenUIState())

    init {

    }



}