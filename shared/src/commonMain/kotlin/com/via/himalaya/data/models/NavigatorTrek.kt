package com.via.himalaya.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class NavigatorTrek(
    @PrimaryKey val id: String,
    val trekId: String,
    val points: List<Point>? = null
)