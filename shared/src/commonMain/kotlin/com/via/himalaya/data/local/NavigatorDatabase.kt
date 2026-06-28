package com.via.himalaya.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.via.himalaya.data.models.NavigatorTrek


@Database(
    entities = [NavigatorTrek::class],
    version = 1
)
@TypeConverters(
    PointListTypeConverter::class
)
abstract class NavigatorDatabase : RoomDatabase() {

    abstract val navigatorDao: NavigatorDao

    companion object {
        const val DATABASE_NAME = "navigator_db"
    }

}