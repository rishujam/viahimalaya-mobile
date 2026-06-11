package com.via.himalaya

import android.app.Application
import android.util.Log
import com.via.himalaya.di.initKoin
import org.koin.android.ext.koin.androidContext

class ViaHimalayaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        Log.d("ViaHimalaya", "🚀 ViaHimalaya Application Starting...")
        initKoin {
            androidContext(this@ViaHimalayaApplication)
        }
    }
}