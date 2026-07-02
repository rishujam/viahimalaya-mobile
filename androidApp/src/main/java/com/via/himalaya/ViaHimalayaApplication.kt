package com.via.himalaya

import android.app.Application
import android.util.Log
import com.via.himalaya.di.initKoin
import com.via.himalaya.domain.Tracker
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext

class ViaHimalayaApplication : Application() {

    private val tracker: Tracker by inject()
    
    override fun onCreate() {
        super.onCreate()
        Log.d("ViaHimalaya", "🚀 ViaHimalaya Application Starting...")
        initKoin {
            androidContext(this@ViaHimalayaApplication)
        }
        
        // Now you can use the tracker
        tracker.track("app_started", mapOf("platform" to "android"))
    }
}