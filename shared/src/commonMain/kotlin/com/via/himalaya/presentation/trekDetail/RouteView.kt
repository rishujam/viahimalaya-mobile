package com.via.himalaya.presentation.trekDetail

import com.via.himalaya.data.models.TrekElevationPoint
import com.via.himalaya.data.models.TrekPoi
import com.via.himalaya.domain.model.RouteNetwork
import com.via.himalaya.domain.model.RouteSection
import com.via.himalaya.domain.model.RouteStep
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Everything the screen shows for the route currently chosen, worked out once per
 * change of route rather than on every recomposition.
 */
data class RouteView(
    val steps: List<RouteStep>,
    /** The chosen route as one line of [lon, lat]. */
    val line: List<List<Double>>,
    /** Sections the route does not use, drawn lighter and tappable. */
    val others: List<RouteSection>,
    /** The sections' profiles joined in walking order, for the elevation slider. */
    val profile: List<TrekElevationPoint>,
    val km: Double,
    val climbM: Int
) {
    companion object {
        fun of(network: RouteNetwork, steps: List<RouteStep>) = RouteView(
            steps = steps,
            line = network.line(steps),
            others = network.others(steps),
            profile = network.profile(steps),
            km = network.km(steps),
            climbM = network.climbM(steps)
        )
    }
}

/**
 * The bundle's POIs measured along [steps]: dist_along_km becomes the distance on
 * this route (sections before it plus how far into its own), so the detail card,
 * the planner and the camp wheel all read the route that is showing. A POI with
 * no placement in the routes file keeps the bundle's figure.
 *
 * Returns the POIs, sorted along the route, and the ids of those that sit beside
 * a section the route does not use - faded on the map.
 */
fun placePois(
    pois: List<TrekPoi>,
    network: RouteNetwork,
    steps: List<RouteStep>
): Pair<List<TrekPoi>, Set<String>> {
    val offRoute = mutableSetOf<String>()
    val placed = pois.map { poi ->
        val placement = poi.id?.let { network.routes.pois[it] } ?: return@map poi
        val km = network.kmAlong(steps, placement)
        if (km == null) {
            poi.id?.let(offRoute::add)
            poi.copy(offsetM = placement.offsetM)
        } else {
            poi.copy(distAlongKm = (km * 1000).toLong() / 1000.0, offsetM = placement.offsetM)
        }
    }
    return placed.sortedBy { it.distAlongKm } to offRoute
}

/** One line as a GeoJSON FeatureCollection, for the trail layer. */
fun lineGeoJson(line: List<List<Double>>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        if (line.size >= 2) add(feature(null, line))
    }
}.toString()

/** Each section as its own feature, carrying its id, for the lighter layer. */
fun sectionsGeoJson(sections: List<RouteSection>): String = buildJsonObject {
    put("type", "FeatureCollection")
    putJsonArray("features") {
        sections.filter { it.coords.size >= 2 }.forEach { add(feature(it.id, it.coords)) }
    }
}.toString()

private fun feature(id: String?, line: List<List<Double>>) = buildJsonObject {
    put("type", "Feature")
    putJsonObject("properties") { id?.let { put("section", it) } }
    putJsonObject("geometry") {
        put("type", "LineString")
        put("coordinates", JsonArray(line.map { p -> JsonArray(p.map { JsonPrimitive(it) }) }))
    }
}
