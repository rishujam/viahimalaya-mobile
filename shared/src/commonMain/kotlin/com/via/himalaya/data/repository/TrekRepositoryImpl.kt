package com.via.himalaya.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.benasher44.uuid.uuid4
import com.via.himalaya.data.database.DatabaseDriverFactory
import com.via.himalaya.data.models.Point
import com.via.himalaya.data.models.RawSensors
import com.via.himalaya.data.models.Trek
import com.via.himalaya.data.models.TrekWithPoints
import com.via.himalaya.database.ViaHimalayaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TrekRepositoryImpl(databaseDriverFactory: DatabaseDriverFactory) : TrekRepository {
    private val database = ViaHimalayaDatabase(databaseDriverFactory.createDriver())
    private val queries = database.viaHimalayaDatabaseQueries
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun startNewTrek(name: String, guideId: String): String = withContext(Dispatchers.IO) {
        val trekId = uuid4().toString()
        val startTime = Clock.System.now()
        
        queries.insertTrek(
            id = trekId,
            name = name,
            guideId = guideId,
            startTime = startTime.epochSeconds,
            isSynced = 0L // false
        )
        
        trekId
    }

    override suspend fun savePoint(trekId: String, point: Point): Unit = withContext(Dispatchers.IO) {
        val rawSensorsJson = point.rawSensors?.let { json.encodeToString(it) }
        
        queries.insertPoint(
            trekId = trekId,
            lat = point.lat,
            lon = point.lon,
            timestamp = point.timestamp.epochSeconds,
            altGps = point.altGps,
            altBaro = point.altBaro,
            accuracyH = point.accuracyH,
            accuracyV = point.accuracyV,
            speed = point.speed,
            bearing = point.bearing,
            battery = point.battery?.toLong(),
            rawSensorsJson = rawSensorsJson
        )
    }

    override suspend fun getUnsyncedTreks(): List<TrekWithPoints> = withContext(Dispatchers.IO) {
        val unsyncedTreks = queries.getUnsyncedTreks().executeAsList()
        
        unsyncedTreks.map { trekEntity ->
            val trek = Trek(
                id = trekEntity.id,
                name = trekEntity.name,
                guideId = trekEntity.guideId,
                startTime = Instant.fromEpochSeconds(trekEntity.startTime),
                isSynced = trekEntity.isSynced == 1L
            )
            
            val points = queries.getPointsByTrekId(trekEntity.id).executeAsList().map { pointEntity ->
                val rawSensors = pointEntity.rawSensorsJson?.let { 
                    try {
                        json.decodeFromString<RawSensors>(it)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                Point(
                    trekId = pointEntity.trekId,
                    lat = pointEntity.lat,
                    lon = pointEntity.lon,
                    timestamp = Instant.fromEpochSeconds(pointEntity.timestamp),
                    altGps = pointEntity.altGps,
                    altBaro = pointEntity.altBaro,
                    accuracyH = pointEntity.accuracyH,
                    accuracyV = pointEntity.accuracyV,
                    speed = pointEntity.speed,
                    bearing = pointEntity.bearing,
                    battery = pointEntity.battery?.toInt(),
                    rawSensors = rawSensors
                )
            }
            
            TrekWithPoints(trek_meta = trek, points = points)
        }
    }

    override fun getTrekById(trekId: String): Flow<Trek?> {
        return queries.getTrekById(trekId)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { trekEntity ->
                Trek(
                    id = trekEntity.id,
                    name = trekEntity.name,
                    guideId = trekEntity.guideId,
                    startTime = Instant.fromEpochSeconds(trekEntity.startTime),
                    isSynced = trekEntity.isSynced == 1L
                )
            }
    }

    override fun getPointsForTrek(trekId: String): Flow<List<Point>> {
        return queries.getPointsByTrekId(trekId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { pointEntities ->
                pointEntities.map { pointEntity ->
                    val rawSensors = pointEntity.rawSensorsJson?.let { 
                        try {
                            json.decodeFromString<RawSensors>(it)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    Point(
                        trekId = pointEntity.trekId,
                        lat = pointEntity.lat,
                        lon = pointEntity.lon,
                        timestamp = Instant.fromEpochSeconds(pointEntity.timestamp),
                        altGps = pointEntity.altGps,
                        altBaro = pointEntity.altBaro,
                        accuracyH = pointEntity.accuracyH,
                        accuracyV = pointEntity.accuracyV,
                        speed = pointEntity.speed,
                        bearing = pointEntity.bearing,
                        battery = pointEntity.battery?.toInt(),
                        rawSensors = rawSensors
                    )
                }
            }
    }

    override fun getAllTreks(): Flow<List<Trek>> {
        return queries.getAllTreks()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { trekEntities ->
                trekEntities.map { trekEntity ->
                    Trek(
                        id = trekEntity.id,
                        name = trekEntity.name,
                        guideId = trekEntity.guideId,
                        startTime = Instant.fromEpochSeconds(trekEntity.startTime),
                        isSynced = trekEntity.isSynced == 1L
                    )
                }
            }
    }

    override suspend fun updateTrekSyncStatus(trekId: String, isSynced: Boolean): Unit = withContext(Dispatchers.IO) {
        queries.updateTrekSyncStatus(
            isSynced = if (isSynced) 1L else 0L,
            id = trekId
        )
    }

    override suspend fun getLatestPointForTrek(trekId: String): Point? = withContext(Dispatchers.IO) {
        val pointEntity = queries.getLatestPointForTrek(trekId).executeAsOneOrNull()
        
        pointEntity?.let {
            val rawSensors = it.rawSensorsJson?.let { jsonString ->
                try {
                    json.decodeFromString<RawSensors>(jsonString)
                } catch (e: Exception) {
                    null
                }
            }
            
            Point(
                trekId = it.trekId,
                lat = it.lat,
                lon = it.lon,
                timestamp = Instant.fromEpochSeconds(it.timestamp),
                altGps = it.altGps,
                altBaro = it.altBaro,
                accuracyH = it.accuracyH,
                accuracyV = it.accuracyV,
                speed = it.speed,
                bearing = it.bearing,
                battery = it.battery?.toInt(),
                rawSensors = rawSensors
            )
        }
    }

    override suspend fun saveBatch(trekId: String, points: List<Point>): Unit = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext
        
        database.transaction {
            points.forEach { point ->
                val rawSensorsJson = point.rawSensors?.let { json.encodeToString(it) }
                
                queries.insertPoint(
                    trekId = trekId,
                    lat = point.lat,
                    lon = point.lon,
                    timestamp = point.timestamp.epochSeconds,
                    altGps = point.altGps,
                    altBaro = point.altBaro,
                    accuracyH = point.accuracyH,
                    accuracyV = point.accuracyV,
                    speed = point.speed,
                    bearing = point.bearing,
                    battery = point.battery?.toLong(),
                    rawSensorsJson = rawSensorsJson
                )
            }
        }
    }
}