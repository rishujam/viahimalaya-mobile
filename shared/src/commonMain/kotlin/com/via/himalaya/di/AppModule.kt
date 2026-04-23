package com.via.himalaya.di

import com.via.himalaya.data.database.DatabaseDriverFactory
import com.via.himalaya.data.repository.TrekRepository
import com.via.himalaya.data.repository.TrekRepositoryImpl

/**
 * Simple dependency injection container for the app
 * iOS-friendly implementation without complex DI frameworks
 */
class AppModule(private val databaseDriverFactory: DatabaseDriverFactory) {
    
    // Lazy initialization of repository
    val trekRepository: TrekRepository by lazy {
        TrekRepositoryImpl(databaseDriverFactory)
    }
}

/**
 * Platform-specific factory for creating AppModule
 */
expect object AppModuleFactory {
    fun create(): AppModule
}