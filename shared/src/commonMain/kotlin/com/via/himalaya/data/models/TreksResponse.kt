package com.via.himalaya.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TreksData(
    val treks: List<TrekDto>,
    val count: Int,
    @SerialName("retrieved_at")
    val retrievedAt: String
) {
    fun toTreks(): List<Trek> {
        return treks.map { it.toTrek() }
    }
}

@Serializable
data class TrekDto(
    val id: String,
    val name: String,
    val location: String,
    val distance: String,
    val elevation: String,
    @SerialName("bounding_box")
    val boundingBox: List<Double> = emptyList(),
    @SerialName("coordinate_url")
    val coordinateUrl: String,
    @SerialName("created_at")
    val createdAt: String
) {
    fun toTrek(): Trek {
        return Trek(
            id = id,
            name = name,
            location = location,
            distance = distance,
            elevation = elevation,
            boundingBox = boundingBox,
            coordinateUrl = coordinateUrl
        )
    }
}

@Serializable
data class TrekDetailData(
    val trek: TrekDto,
    @SerialName("retrieved_at")
    val retrievedAt: String
) {
    fun toTrekDetail(): TrekDetail {
        return TrekDetail(
            id = trek.id,
            name = trek.name,
            location = trek.location,
            distance = trek.distance,
            elevation = trek.elevation,
            boundingBox = trek.boundingBox,
            coordinateUrl = trek.coordinateUrl
        )
    }
}
