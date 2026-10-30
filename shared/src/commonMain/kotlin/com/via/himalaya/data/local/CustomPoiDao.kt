package com.via.himalaya.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.via.himalaya.data.models.CustomPoi
import com.via.himalaya.data.models.CustomPoiStatus

@Dao
interface CustomPoiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(poi: CustomPoi)

    /** Oldest first, so places arrive in the order they were saved. */
    @Query("SELECT * FROM CustomPoi WHERE status = '${CustomPoiStatus.PENDING}' ORDER BY createdAt")
    suspend fun getPending(): List<CustomPoi>

    @Query(
        "UPDATE CustomPoi SET status = '${CustomPoiStatus.UPLOADED}', " +
            "attempts = attempts + 1, lastError = NULL WHERE id = :id"
    )
    suspend fun markUploaded(id: String)

    @Query(
        "UPDATE CustomPoi SET status = '${CustomPoiStatus.FAILED}', " +
            "attempts = attempts + 1, lastError = :error WHERE id = :id"
    )
    suspend fun markFailed(id: String, error: String)

    /** A try that reached no verdict (no connection, server down). The row stays PENDING. */
    @Query("UPDATE CustomPoi SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun recordAttempt(id: String, error: String)
}
