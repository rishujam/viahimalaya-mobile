package com.via.himalaya.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.via.himalaya.data.models.TrekDetail

@Dao
interface TrekDao {

    @Upsert
    suspend fun insert(trek: TrekDetail)

    @Query("SELECT * FROM TrekDetail WHERE id = :id")
    suspend fun getTrek(id: String): TrekDetail?

    @Query("SELECT * FROM TrekDetail")
    suspend fun getTreks(): List<TrekDetail>?

    @Query("DELETE FROM TrekDetail WHERE id = :id")
    suspend fun deleteTrek(id: String)

}