package com.via.himalaya.data.models

import kotlinx.serialization.Serializable

/**
 * One sample along the trail: where it is, and how high the ground is there.
 *
 * Deliberately a list element rather than a `Map<Pair<Double, Double>, String>`:
 *
 *  - the slider addresses points by position along the walk, so it needs index
 *    lookup on every drag frame - a map would mean rebuilding a list each time
 *  - order along the trail *is* the data here, and a map does not promise any
 *  - a `Pair<Double, Double>` key means looking things up by floating point
 *    equality, which never ends well
 *  - the height is a number, and keeping it one leaves room for the profile
 *    chart and total-gain figures without reparsing strings
 *
 * Samples are evenly spaced along the trail (100 m), which is what makes index
 * position a stand-in for distance travelled.
 */
@Serializable
data class TrekElevationPoint(
    val lat: Double,
    val lon: Double,
    /** Metres above sea level. GLO-30 is already geoid-referenced. */
    val elevationM: Int
)

/**
 * Converts the wire format into points.
 *
 * The API sends `[[lat, lon, metres], ...]` rather than objects - three repeated
 * keys across 250-500 samples cost about 70% more bytes for no benefit, since
 * nothing queries inside the array.
 *
 * Malformed triples are dropped rather than thrown on: a truncated sample should
 * cost the slider one point, not take down the trek screen.
 */
fun List<List<Double>>.toElevationPoints(): List<TrekElevationPoint> =
    mapNotNull { triple ->
        if (triple.size < 3) null
        else TrekElevationPoint(
            lat = triple[0],
            lon = triple[1],
            elevationM = triple[2].toInt()
        )
    }
