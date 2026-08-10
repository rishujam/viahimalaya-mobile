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
    val coordinateUrl: String,
    val imageUrl: String? = null,
    val poiUrl: String? = null,
    /**
     * Bundle version last seen for this trek. The R2 key never changes, so this
     * is the only signal that a regenerated bundle needs re-downloading.
     */
    val poiUpdatedAt: String? = null,
    /** External write-up. Read from Room for downloaded treks, so it must live here. */
    val detailsUrl: String? = null,
    /**
     * Ground height every 100 m along the trail, driving the elevation slider.
     *
     * Null for any trek the backend has not profiled yet, and for treks
     * downloaded before this column existed - the slider is simply not drawn in
     * that case. Stored on the entity rather than fetched separately because
     * getTrek() reads Room first for downloaded treks, so anything the UI needs
     * has to survive here.
     */
    val elevationProfile: List<TrekElevationPoint>? = null
) {

    fun toTrek(): Trek {
        return Trek(
            id = id,
            name = name,
            location = location,
            distance = distance,
            elevation = elevation,
            coordinateUrl = coordinateUrl,
            boundingBox = boundingBox,
            imageUrl = imageUrl,
            poiUrl = poiUrl,
            poiUpdatedAt = poiUpdatedAt,
            detailsUrl = detailsUrl
        )
    }

}
