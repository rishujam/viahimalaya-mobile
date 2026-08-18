package com.via.himalaya.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.data.models.TrekPlan

@Database(
    entities = [TrekDetail::class, TrekPlan::class],
    version = 4
)
@TypeConverters(
    DoubleListTypeConverter::class,
    ElevationProfileTypeConverter::class,
    PlannedDayListTypeConverter::class
)
abstract class TrekDatabase : RoomDatabase() {

    abstract val trekDao: TrekDao
    abstract val trekPlanDao: TrekPlanDao

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

        /**
         * Adds saved itineraries.
         *
         * Deliberately no foreign key to TrekDetail: a plan outlives the
         * download it was made alongside, and can exist for a trek that was
         * never downloaded. The index is on trekId because every read is "the
         * plans for this trek" - planId is only ever used to delete one.
         *
         * NOT NULL with defaults on the columns Room expects non-null, so the
         * generated schema and this statement agree; Room validates them against
         * each other on open and throws if they drift.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS TrekPlan (
                        planId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        trekId TEXT NOT NULL,
                        days TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_TrekPlan_trekId ON TrekPlan (trekId)"
                )
            }
        }
    }

}