package com.via.himalaya.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Where one day of a plan ends - the camp you sleep at.
 *
 * Distance and climb are deliberately absent. Both are derived: distance is the
 * difference between two `dist_along_km` values, climb is a walk over the
 * elevation profile. Storing them would mean storing a snapshot of a trek that
 * can change underneath it - reversing Rupin's direction rewrote every
 * `dist_along_km` in its bundle, and any stored distance would have gone on
 * quietly describing the old direction.
 *
 * The name and coordinates are not for display convenience, they are an anchor.
 * OSM ids churn: a node deleted and re-added gets a new one. If [poiId] stops
 * matching anything, the plan can still be read, and a future pass could re-match
 * it by proximity.
 */
@Serializable
data class PlannedDay(
    /** OSM element id, e.g. "node/8921313200". Null for a user-added camp. */
    val poiId: String? = null,
    val campName: String? = null,
    val lat: Double,
    val lon: Double
)

/**
 * One saved itinerary for a trek.
 *
 * Several plans per trek are allowed on purpose - comparing a fast four-day
 * version against a gentler six-day one is the point of planning - so the key is
 * [planId] rather than the trek.
 *
 * No foreign key to TrekDetail. A plan and a downloaded trek have separate
 * lifetimes: deleting the download must leave the plan alone, and a plan can
 * exist for a trek that was never downloaded at all.
 *
 * Day numbers are list positions in [days]. Storing them as a field would create
 * two sources of truth that could disagree.
 */
@Entity(indices = [Index("trekId")])
data class TrekPlan(
    /** Assigned by Room on insert - pass 0 and it fills in the next id. */
    @PrimaryKey(autoGenerate = true) val planId: Long = 0,
    /** Not unique: several plans for one trek is the whole point. */
    val trekId: String,
    val days: List<PlannedDay> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long
) {
    /**
     * Days walked, which is one more than the nights: the last leg runs from the
     * final camp to the end of the trail and is never chosen.
     */
    val dayCount: Int get() = days.size + 1
}
