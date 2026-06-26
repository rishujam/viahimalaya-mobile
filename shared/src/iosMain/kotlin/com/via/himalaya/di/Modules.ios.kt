package com.via.himalaya.di

import com.via.himalaya.data.local.DatabaseFactory
import com.via.himalaya.data.local.FileDownloader
import com.via.himalaya.data.local.IosFileDownloader
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module
    get() = module {
        single<HttpClientEngine> { Darwin.create() }
        single { DatabaseFactory() }
        single { IosFileDownloader(get()) }.bind<FileDownloader>()
    }