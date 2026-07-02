package com.via.himalaya.di

import com.via.himalaya.data.local.AndroidFileDownloader
import com.via.himalaya.data.local.DatabaseFactory
import com.via.himalaya.data.local.FileDownloader
import com.via.himalaya.data.local.NavigatorDatabaseFactory
import com.via.himalaya.data.local.createDatastore
import com.via.himalaya.domain.AndroidLocationEmitter
import com.via.himalaya.domain.AndroidSensorListener
import com.via.himalaya.domain.LocationEmitter
import com.via.himalaya.domain.SensorListener
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
    }