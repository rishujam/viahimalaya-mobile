package com.via.himalaya.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.via.himalaya.database.ViaHimalayaDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(ViaHimalayaDatabase.Schema, "viahimalaya.db")
    }
}