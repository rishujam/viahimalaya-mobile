package com.via.himalaya.data.local

import androidx.room.TypeConverter
import com.via.himalaya.data.models.PlannedDay
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PlannedDayListTypeConverter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Unreadable text yields an empty day list rather than throwing.
     *
     * A plan is the user's own work, so losing it silently is bad - but taking
     * down the plan screen on open is worse, and offline there would be no way
     * back. An empty plan is visible and deletable; a crash is neither.
     */
    @TypeConverter
    fun fromString(value: String?): List<PlannedDay> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<PlannedDay>>(value) }
            .getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromList(value: List<PlannedDay>): String = json.encodeToString(value)
}
