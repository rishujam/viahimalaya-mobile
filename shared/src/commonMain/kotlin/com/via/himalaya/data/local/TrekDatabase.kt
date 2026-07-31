package com.via.himalaya.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.via.himalaya.data.models.TrekDetail

@Database(
    entities = [TrekDetail::class],
    version = 2
)
@TypeConverters(
    DoubleListTypeConverter::class
)
abstract class TrekDatabase : RoomDatabase() {

    abstract val trekDao: TrekDao

    companion object {
        const val DATABASE_NAME = "trek_db"

        /**
         * Adds the POI bundle pointers and the external details link. All
         * nullable, so downloaded treks from v1 survive the upgrade and simply
         * re-fetch on next open.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE TrekDetail ADD COLUMN poiUrl TEXT")
                connection.execSQL("ALTER TABLE TrekDetail ADD COLUMN poiUpdatedAt TEXT")
                connection.execSQL("ALTER TABLE TrekDetail ADD COLUMN detailsUrl TEXT")
            }
        }
    }

}