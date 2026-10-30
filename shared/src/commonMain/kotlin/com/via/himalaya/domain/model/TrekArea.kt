package com.via.himalaya.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * Whether a point is somewhere a trek goes.
 *
 * A trek's bounding box is drawn tight on the trail, so a camp 300 m to the side
 * of the westernmost bend lies outside it. [PADDING_M] is the margin that makes
 * such a camp count; it matches how far off the trail the planner will take you.
 *
 * Mirrors isInsideTrekArea in the web repo's lib/customPoi.ts, number for
 * number. Here it stops the user early; there it is the check that counts. Change
 * one and change the other.
 */
object TrekArea {

    const val PADDING_M = 1000.0

    private const val METRES_PER_DEGREE_LAT = 111_320.0

    /** [boundingBox] is [minLon, minLat, maxLon, maxLat], as stored on the trek. */
    fun contains(
        lat: Double,
        lon: Double,
        boundingBox: List<Double>,
        paddingM: Double = PADDING_M
    ): Boolean {
        if (boundingBox.size < 4) return false
        val minLon = boundingBox[0]
        val minLat = boundingBox[1]
        val maxLon = boundingBox[2]
        val maxLat = boundingBox[3]

        val dLat = paddingM / METRES_PER_DEGREE_LAT
        // A degree of longitude shrinks with latitude. Clamped so a point near a
        // pole cannot divide by (almost) zero; no trek is anywhere near one.
        val cosLat = max(cos(lat * PI / 180.0), 0.01)
        val dLon = paddingM / (METRES_PER_DEGREE_LAT * cosLat)

        return lat >= minLat - dLat && lat <= maxLat + dLat &&
            lon >= minLon - dLon && lon <= maxLon + dLon
    }
}
