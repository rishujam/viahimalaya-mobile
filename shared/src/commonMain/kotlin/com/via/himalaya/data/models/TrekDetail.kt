package com.via.himalaya.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class TrekDetail(
    @PrimaryKey val id: String,
    val name: String,
    val location: String,
    val distance: String,
    val elevation: String,
    val boundingBox: List<Double>,
    val imageUrl: String? = null,
    /** External write-up. Read from Room for downloaded treks, so it must live here. */
    val detailsUrl: String? = null,
    /**
     * The routes file (trek_routes/<id>.json): the trail, every other path, the
     * elevation profiles and the POIs. Null on a trek downloaded before routes
     * existed: that download is redone (TrekRepository.refreshDownloadedTrek).
     */
    val routesUrl: String? = null,
    /** Version of the routes file; a change means re-downloading it. */
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
