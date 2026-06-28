package com.via.himalaya.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class NavigatorDatabaseFactory(
    private val context: Context
) {
    actual fun create(): RoomDatabase.Builder<NavigatorDatabase> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(NavigatorDatabase.DATABASE_NAME)
        return Room.databaseBuilder<NavigatorDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        )
    }

}