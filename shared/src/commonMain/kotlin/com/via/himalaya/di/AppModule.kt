package com.via.himalaya.di

import com.via.himalaya.data.database.DatabaseDriverFactory
import com.via.himalaya.data.repository.TrekRepository
import com.via.himalaya.data.repository.TrekRepositoryImpl
import com.via.himalaya.location.LocationBufferManager
import com.via.himalaya.location.BufferConfig
import com.via.himalaya.presentation.TrekTrackingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Simple dependency injection container for the app
 * iOS-friendly implementation without complex DI frameworks
 */
class AppModule(private val databaseDriverFactory: DatabaseDriverFactory) {
    
    // Lazy initialization of repository
    val trekRepository: TrekRepository by lazy {
        TrekRepositoryImpl(databaseDriverFactory)
    }
    
    // Lazy initialization of LocationBufferManager
    val locationBufferManager: LocationBufferManager by lazy {
        LocationBufferManager(
            trekRepository = trekRepository,
            config = BufferConfig(), // Use default config
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        )
    }
    
    // Factory method for creating ViewModels
    fun createTrekTrackingViewModel(): TrekTrackingViewModel {
        return TrekTrackingViewModel(trekRepository, locationBufferManager)
    }
}

/**
 * Platform-specific factory for creating AppModule
 */
expect object AppModuleFactory {
    fun create(): AppModule
}