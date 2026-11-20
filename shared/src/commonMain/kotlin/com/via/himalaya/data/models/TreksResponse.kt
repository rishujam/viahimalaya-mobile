package com.via.himalaya.data.models

import com.via.himalaya.domain.model.Page
import com.via.himalaya.domain.model.Treks
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    @SerialName("total_pages")
    val totalPages: Int,
    @SerialName("has_next")
    val hasNext: Boolean,
    @SerialName("has_prev")
    val hasPrev: Boolean,
    val seed: String
)

@Serializable
data class TreksData(
    val treks: List<TrekDto>,
    val pagination: Pagination? = null,
    val count: Int? = null, // Keep for backward compatibility
    @SerialName("retrieved_at")
    val retrievedAt: String
) {
    fun toTreks(): Treks {
        return Treks(
            treks = treks.map { it.toTrek() },
            page = Page(
                seed = pagination?.seed,
                pageNo = pagination?.page ?: 1,
                hasNext = pagination?.hasNext ?: false,
                total = pagination?.total ?: count ?: 0,
                totalPages = pagination?.totalPages ?: 0
            )
        )
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
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("details_url")
    val detailsUrl: String? = null,
    /**
     * The routes file on R2: every section of the trek, the default route and its
     * POIs. The API also still sends coordinate_url, poi_url, poi_updated_at and
     * (detail only) elevation_profile for older apps; this build ignores them.
     */
    @SerialName("routes_url")
    val routesUrl: String? = null,
    /** When the routes file was regenerated; the cached copy is replaced when this changes. */
    @SerialName("routes_updated_at")
    val routesUpdatedAt: String? = null
) {
    fun toTrek(): Trek {
        return Trek(
            id = id,
            name = name,
            location = location,
            distance = distance,
            elevation = elevation,
            boundingBox = boundingBox,
            imageUrl = imageUrl,
            detailsUrl = detailsUrl
        )
    }
}

@Serializable
data class TrekSearchData(
    val query: String,
    val count: Int,
    val treks: List<TrekDto>,
    @SerialName("searched_at")
    val searchedAt: String
) {
    fun toTrekList(): List<Trek> {
        return treks.map { it.toTrek() }
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
            imageUrl = trek.imageUrl,
            detailsUrl = trek.detailsUrl,
            routesUrl = trek.routesUrl,
            routesUpdatedAt = trek.routesUpdatedAt
        )
    }
}
