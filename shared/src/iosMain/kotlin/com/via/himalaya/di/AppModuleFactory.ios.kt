package com.via.himalaya.di

import com.via.himalaya.data.database.DatabaseDriverFactory

actual object AppModuleFactory {
    actual fun create(): AppModule {
        return AppModule(DatabaseDriverFactory())
    }
}