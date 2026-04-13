package com.via.himalaya.di

import android.content.Context
import com.via.himalaya.data.database.DatabaseDriverFactory

actual object AppModuleFactory {
    private var context: Context? = null
    
    fun initialize(context: Context) {
        this.context = context
    }
    
    actual fun create(): AppModule {
        val context = requireNotNull(this.context) { 
            "AppModuleFactory must be initialized with context before use" 
        }
        return AppModule(DatabaseDriverFactory(context))
    }
}