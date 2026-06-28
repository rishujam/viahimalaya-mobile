package com.via.himalaya.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.via.himalaya.data.models.NavigatorTrek
import com.via.himalaya.data.models.Point

@Dao
interface NavigatorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrek(trek: NavigatorTrek)

    @Query("SELECT * FROM NavigatorTrek")
    suspend fun getAllNavigatorTreks(): List<NavigatorTrek>

    @Query("SELECT * FROM NavigatorTrek WHERE id = :id")
    suspend fun getNavigatorTrekById(id: String): NavigatorTrek?

    @Transaction
    suspend fun updateNavigatorTrek(id: String, newPoints: List<Point>) {
        val existingTrek = getNavigatorTrekById(id)
        if (existingTrek != null) {
            val updatedPoints = (existingTrek.points ?: emptyList()) + newPoints
            val updatedTrek = existingTrek.copy(points = updatedPoints)
            insertTrek(updatedTrek)
        }
    }

}