package com.via.himalaya.data.local

import androidx.room.TypeConverter
import com.via.himalaya.data.models.Point
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PointListTypeConverter {

    @TypeConverter
    fun fromString(value: String?): List<Point>? {
        return value?.let { Json.decodeFromString<List<Point>>(it) }
    }

    @TypeConverter
    fun fromList(value: List<Point>?): String? {
        return value?.let { Json.encodeToString(it) }
    }

}
