package com.via.himalaya.data.local

import androidx.room.TypeConverter
import com.via.himalaya.data.models.TrekElevationPoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ElevationProfileTypeConverter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns null rather than throwing on unreadable stored text.
     *
     * This column is nullable by design - a trek with no profile just does not
     * draw the slider - so a row written by an older build, or truncated, should
     * degrade to that same no-slider state. Throwing here would take down the
     * trek screen for a downloaded trek, offline, which is exactly when the user
     * has no way to recover.
     */
    @TypeConverter
    fun fromString(value: String?): List<TrekElevationPoint>? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString<List<TrekElevationPoint>>(value)
        }.getOrNull()
    }

    @TypeConverter
    fun fromList(value: List<TrekElevationPoint>?): String? {
        return value?.let { json.encodeToString(it) }
    }
}
