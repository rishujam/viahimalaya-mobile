package com.via.himalaya

import android.app.Application
import android.util.Log
import com.via.himalaya.di.initKoin
import com.via.himalaya.domain.Tracker
import com.via.himalaya.domain.repo.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext

class ViaHimalayaApplication : Application() {

    private val tracker: Tracker by inject()
    private val authRepository: AuthRepository by inject()
    
    override fun onCreate() {
        super.onCreate()
        Log.d("ViaHimalaya", "🚀 ViaHimalaya Application Starting...")
        
        // Initialize Koin (Mapbox TileStore configured in DI)
        initKoin {
            androidContext(this@ViaHimalayaApplication)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val user = authRepository.getCurrentUser()
            Log.d("ViaHimalaya", "Current user: $user")
            user?.let {
                tracker.setUser(user.email)
            }
            tracker.track(
                "app_started",
                mapOf("platform" to "android")
            )
        }
    }
}