package com.via.himalaya.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.via.himalaya.data.models.TrekDetail

@Database(
    entities = [TrekDetail::class],
    version = 1
)
@TypeConverters(
    DoubleListTypeConverter::class
)
abstract class TrekDatabase : RoomDatabase() {

    abstract val trekDao: TrekDao

    companion object {
        const val DATABASE_NAME = "trek_db"
    }

}