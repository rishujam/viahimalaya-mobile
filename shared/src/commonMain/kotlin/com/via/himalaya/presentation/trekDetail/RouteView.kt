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
 * The trek's POIs, from the routes file, measured along [steps]: dist_along_km is
 * the distance on this route (sections before it plus how far into its own), so
 * the detail card, the planner and the camp wheel all read the route showing.
 *
 * Returns them sorted along the route, and the ids of those beside a section the
 * route does not use - faded on the map, left out of the planner, and described
 * by the card as on another path. Those have no distance on this route; they
 * keep 0 and sort first.
 */
fun placePois(network: RouteNetwork, steps: List<RouteStep>): Pair<List<TrekPoi>, Set<String>> {
    val offRoute = mutableSetOf<String>()
    val placed = network.routes.pois.map { poi ->
        val km = network.kmAlong(steps, poi.section, poi.atKm)
        if (km == null) poi.id?.let(offRoute::add)
        TrekPoi(
            id = poi.id,
            category = poi.category,
            type = poi.type,
            name = poi.name,
            lat = poi.lat,
            lon = poi.lon,
            distAlongKm = km?.let { (it * 1000).toLong() / 1000.0 } ?: 0.0,
            offsetM = poi.offsetM,
            offRoute = poi.offRoute,
            eleM = poi.eleM,
            approxCenter = poi.approxCenter,
            occupancyRank = poi.occupancyRank,
            tags = poi.tags
        )
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
