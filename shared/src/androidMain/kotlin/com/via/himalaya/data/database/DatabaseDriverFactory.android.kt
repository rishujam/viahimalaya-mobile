package com.via.himalaya.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.via.himalaya.database.ViaHimalayaDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(ViaHimalayaDatabase.Schema, context, "viahimalaya.db")
    }
}