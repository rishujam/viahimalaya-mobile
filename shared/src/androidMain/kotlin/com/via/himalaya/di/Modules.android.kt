package com.via.himalaya.di

import android.util.Log
import com.mapbox.bindgen.Value
import com.mapbox.common.MapboxOptions
import com.mapbox.common.TileStore
import com.mapbox.common.TileStoreOptions
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.mapsOptions
import com.via.himalaya.data.local.AndroidFileDownloader
import com.via.himalaya.data.local.DatabaseFactory
import com.via.himalaya.data.local.FileDownloader
import com.via.himalaya.data.local.NavigatorDatabaseFactory
import com.via.himalaya.data.local.OfflineMapManager
import com.via.himalaya.data.local.createDatastore
import com.via.himalaya.domain.AndroidLocationEmitter
import com.via.himalaya.domain.AndroidSensorListener
import com.via.himalaya.domain.LocationEmitter
import com.via.himalaya.domain.SensorListener
import com.via.himalaya.data.local.AndroidOfflineMapManager
import org.koin.android.ext.koin.androidApplication
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module
    get() = module {
        single<HttpClientEngine> { OkHttp.create() }
        single { DatabaseFactory(androidApplication()) }
        single { NavigatorDatabaseFactory(androidApplication()) }
        single { AndroidFileDownloader(androidApplication(), get()) }.bind<FileDownloader>()
        single { AndroidLocationEmitter(androidApplication()) }.bind<LocationEmitter>()
        single { AndroidSensorListener(androidApplication()) }.bind<SensorListener>()
        single { createDatastore(androidApplication()) }
        
        // Offline Map Manager - Android only (Mapbox SDK)
        // Configure TileStore with 500MB quota for offline downloads
        single<OfflineMapManager> {
            try {
                val tileStore = MapboxOptions.mapsOptions.tileStore ?: TileStore.create()
                
                // Set disk quota to 500MB for offline trek tiles
                // This allows multiple treks to be downloaded (typical trek: 5-100MB)
                tileStore.setOption(
                    TileStoreOptions.DISK_QUOTA,
                    Value.valueOf(500L * 1024 * 1024) // 500 MB
                )
                
                val offlineManager = OfflineManager()
                Log.d("ViaHimalaya", "✅ OfflineMapManager configured: 500MB disk quota")
                
                AndroidOfflineMapManager(tileStore, offlineManager)
            } catch (e: Exception) {
                Log.e("ViaHimalaya", "❌ Error configuring OfflineMapManager", e)
                throw e
            }
        }
    }
