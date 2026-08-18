package com.via.himalaya.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.via.himalaya.data.models.TrekPlan

@Dao
interface TrekPlanDao {

    /** Returns the id Room assigned, so the caller can show the plan it just saved. */
    @Insert
    suspend fun insert(plan: TrekPlan): Long

    /** Newest first - the plan you just made is the one you want to see. */
    @Query("SELECT * FROM TrekPlan WHERE trekId = :trekId ORDER BY updatedAt DESC")
    suspend fun getPlansForTrek(trekId: String): List<TrekPlan>

    @Query("DELETE FROM TrekPlan WHERE planId = :planId")
    suspend fun deletePlan(planId: Long)
}
