package com.via.himalaya.data.repository

import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekWithPoints
import kotlinx.coroutines.flow.Flow

interface TrekRepository {

    /**
     * Starts a new trek and returns its ID
     */
    suspend fun startNewTrek(name: String, guideId: String): String

    /**
     * Gets a trek by ID as a Flow for reactive updates
     */
    fun getTrekById(trekId: String): Flow<Trek?>

    /**
     * Gets points for a specific trek as a Flow for reactive updates
     */
    fun getPointsForTrek(trekId: String): Flow<List<Point>>

    /**
     * Gets all treks as a Flow for reactive updates
     */
    fun getAllTreks(): Flow<List<Trek>>

    /**
     * Saves multiple points in a single transaction for better performance
     * Used by LocationBufferManager for batch operations
     */
    suspend fun saveBatch(trekId: String, points: List<Point>): Unit

    /**
     * Saves a GPS point linked to a trek
     */
    suspend fun savePoint(trekId: String, point: Point): Unit

    /**
     * Returns a list of unsynced treks with their points in the required JSON format
     */
    suspend fun getUnsyncedTreks(): List<TrekWithPoints>

    /**
     * Updates the sync status of a trek
     */
    suspend fun updateTrekSyncStatus(trekId: String, isSynced: Boolean): Unit

    /**
     * Gets the latest point for a trek
     */
    suspend fun getLatestPointForTrek(trekId: String): Point?

}