package com.via.himalaya.di

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.via.himalaya.presentation.TrekViewModel

/**
 * ViewModelFactory for TrekViewModel following official KMP guidelines
 * This ensures proper lifecycle management and configuration change survival
 */
val trekViewModelFactory = viewModelFactory {
    initializer {
        val appModule = AppModuleFactory.create()
        TrekViewModel(trekRepository = appModule.trekRepository)
    }
}