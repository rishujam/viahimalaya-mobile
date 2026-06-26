package com.via.himalaya.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object DoubleListTypeConverter {

    @TypeConverter
    fun fromString(value: String): List<Double> {
        return Json.decodeFromString<List<Double>>(value)
    }

    @TypeConverter
    fun fromList(value: List<Double>): String {
        return Json.encodeToString(value)
    }

}