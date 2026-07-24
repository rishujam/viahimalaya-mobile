package com.via.himalaya.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity
data class NavigatorTrek(
    @PrimaryKey val id: String,
    val user: String,
    val trekId: String,
    val points: List<Point>? = null
)