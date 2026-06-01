package com.via.himalaya

import android.app.Application
import android.util.Log
import com.via.himalaya.di.AppModuleFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ViaHimalayaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d("ViaHimalaya", "🚀 ViaHimalaya Application Starting...")
        
        // Initialize the AppModuleFactory with application context
        AppModuleFactory.initialize(this)
        
        // Initialize database and log table information
//        initializeDatabase()
    }
}