package com.via.himalaya.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual class DatabaseFactory(
    private val context: Context
) {
    actual fun create(): RoomDatabase.Builder<TrekDatabase> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(TrekDatabase.DATABASE_NAME)
        return Room.databaseBuilder<TrekDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        )
    }

}