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
    version = 3
)
@TypeConverters(
    DoubleListTypeConverter::class,
    ElevationProfileTypeConverter::class
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

        /**
         * Adds the elevation profile behind the detail screen's slider.
         *
         * Nullable and left empty for existing rows: a trek downloaded before
         * this simply shows no slider until it is re-downloaded, which is the
         * same state as a trek the backend has not profiled yet. Nothing else
         * degrades, so there is no reason to force a re-download.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE TrekDetail ADD COLUMN elevationProfile TEXT")
            }
        }
    }

}