package com.via.himalaya.domain.model

import com.via.himalaya.data.models.TrekElevationPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/** One section of a route, walked forwards (from -> to) or [reversed]. */
data class RouteStep(val sectionId: String, val reversed: Boolean = false)

/**
 * The sections of one trek and what a route through them looks like.
 *
 * A route is a list of [RouteStep]s, each starting where the one before ends. The
 * app only ever builds the default and changes it one tap at a time - it never
 * routes freely through the network (HANDOFF section 53 item 4). A tap on a
 * section that is not on the route does one of two things, decided by where the
 * section's two ends are:
 *
 *  - both on the route, at different nodes: an alternative. It **replaces** the
 *    stretch of the route between those nodes.
 *  - both on the route at the same node (a loop), or one on the route and the
 *    other a dead end (a side trip): it is **added** there - a side trip out and
 *    back the same way. Tapping it again takes it off.
 *
 * Anything else - a section branching off a path that is not on the route - is
 * not tappable until that path is chosen.
 */
class RouteNetwork(val routes: TrekRoutes) {

    private val byId = routes.sections.associateBy { it.id }

    /** Section ends at each node. A dead end has one. */
    private val degree: Map<String, Int> = buildMap {
        routes.sections.forEach { s ->
            put(s.from, (get(s.from) ?: 0) + 1)
            put(s.to, (get(s.to) ?: 0) + 1)
        }
    }

    fun section(id: String): RouteSection? = byId[id]

    private fun startOf(step: RouteStep): String {
        val s = byId.getValue(step.sectionId)
        return if (step.reversed) s.to else s.from
    }

    private fun endOf(step: RouteStep): String {
        val s = byId.getValue(step.sectionId)
        return if (step.reversed) s.from else s.to
    }

    /**
     * The file's default route, each section turned to follow on from the last.
     * Null when it names a section that is not in the file, or does not join up:
     * a broken file, which the screen treats like a missing one.
     */
    fun defaultRoute(): List<RouteStep>? {
        val ids = routes.defaultRoute
        if (ids.isEmpty() || ids.any { it !in byId }) return null
        val first = byId.getValue(ids[0])
        val firstReversed = ids.size > 1 && byId.getValue(ids[1]).let { next ->
            first.to != next.from && first.to != next.to
        }
        val steps = mutableListOf(RouteStep(first.id, firstReversed))
        for (id in ids.drop(1)) {
            val s = byId.getValue(id)
            val here = endOf(steps.last())
            steps += when (here) {
                s.from -> RouteStep(id, reversed = false)
                s.to -> RouteStep(id, reversed = true)
                else -> return null
            }
        }
        return steps
    }

    /** The nodes a route passes, in order: its start, then the end of every step. */
    fun nodes(steps: List<RouteStep>): List<String> =
        if (steps.isEmpty()) emptyList() else listOf(startOf(steps[0])) + steps.map(::endOf)

    private fun backwards(chain: List<RouteStep>) =
        chain.asReversed().map { it.copy(reversed = !it.reversed) }

    /**
     * The shortest way from [start] to any node of the route, over sections not in
     * [avoid]: the route node it reaches, and the steps from that node to [start].
     * Empty steps when [start] is on the route already; null when it cannot reach it.
     */
    private fun wayBack(start: String, onRoute: Set<String>, avoid: Set<String>): Pair<String, List<RouteStep>>? {
        if (start in onRoute) return start to emptyList()
        val best = mutableMapOf(start to 0.0)
        val cameBy = mutableMapOf<String, RouteStep>()     // node -> step that reached it, walked from start
        val queue = mutableListOf(start)
        while (queue.isNotEmpty()) {
            val here = queue.minBy { best.getValue(it) }
            queue.remove(here)
            if (here in onRoute) {
                // Walk the found way back to start, then turn it round.
                val fromStart = mutableListOf<RouteStep>()
                var node = here
                while (node != start) {
                    val step = cameBy.getValue(node)
                    fromStart += step
                    node = startOf(step)
                }
                return here to backwards(fromStart.asReversed())
            }
            routes.sections.forEach { s ->
                if (s.id in avoid || s.from == s.to) return@forEach
                val step = when (here) {
                    s.from -> RouteStep(s.id, reversed = false)
                    s.to -> RouteStep(s.id, reversed = true)
                    else -> return@forEach
                }
                val next = endOf(step)
                val d = best.getValue(here) + s.km
                if (d < (best[next] ?: Double.MAX_VALUE)) {
                    best[next] = d
                    cameBy[next] = step
                    if (next !in queue) queue += next
                }
            }
        }
        return null
    }

    /**
     * The route after a tap on [sectionId], or null when the tap changes nothing.
     *
     * Off the route: the shortest way from each end of the tapped section back to
     * the route (over sections the route does not use) makes a chain through it.
     * Both ends reach the route at different nodes: the chain replaces the stretch
     * between them. At the same node: it is a loop, added there. Only one end
     * reaches it: a side trip, out to the tapped section's far end and back.
     * Going back from Hargaon to the Juda ka Talab way on Kedarkantha is a tap on
     * either of its two sections: the chain is both.
     *
     * On the route: a side trip or loop it was added as comes off again; any other
     * part of the route stays, since the route would not join up without it.
     */
    fun tap(steps: List<RouteStep>, sectionId: String): List<RouteStep>? {
        val s = byId[sectionId] ?: return null
        val at = steps.indexOfFirst { it.sectionId == sectionId }
        if (at >= 0) return removeDetour(steps, at)

        val nodes = nodes(steps)
        val onRoute = nodes.toSet()
        val used = steps.mapTo(HashSet()) { it.sectionId } + sectionId

        val back = wayBack(s.from, onRoute, used)
        val ahead = wayBack(s.to, onRoute, used + back?.second.orEmpty().map { it.sectionId })
            ?.let { (node, steps) -> node to backwards(steps) }   // now runs s.to -> node

        if (back != null && ahead != null) {
            val chain = back.second + RouteStep(s.id) + ahead.second
            val (x, y) = back.first to ahead.first
            if (x == y) {                                           // a loop
                val i = nodes.indexOf(x)
                return steps.take(i) + chain + steps.drop(i)
            }
            val forward = nodes.indexOf(x) < nodes.indexOf(y)      // an alternative
            val i = nodes.indexOf(if (forward) x else y)
            val other = if (forward) y else x
            val j = (i + 1 until nodes.size).first { nodes[it] == other }
            return steps.take(i) + (if (forward) chain else backwards(chain)) + steps.drop(j)
        }
        val out = when {                                            // a side trip
            back != null -> back.second + RouteStep(s.id)
            ahead != null -> backwards(listOf(RouteStep(s.id)) + ahead.second)
            else -> return null
        }
        val i = nodes.indexOf(back?.first ?: ahead!!.first)
        return steps.take(i) + out + backwards(out) + steps.drop(i)
    }

    /**
     * Takes off the side trip or loop that step [at] belongs to: the shortest run of
     * steps around it that starts and ends at the same node, widened while the
     * steps either side are the same section walked out and back. Null when there
     * is none, or it would be the whole route (a circular trek's own path).
     */
    private fun removeDetour(steps: List<RouteStep>, at: Int): List<RouteStep>? {
        for (len in 1 until steps.size) {
            for (i in (at - len + 1)..at) {
                var a = i
                var b = i + len - 1
                if (a < 0 || b >= steps.size || startOf(steps[a]) != endOf(steps[b])) continue
                while (a > 0 && b < steps.size - 1 &&
                    steps[a - 1].sectionId == steps[b + 1].sectionId &&
                    steps[a - 1].reversed != steps[b + 1].reversed
                ) {
                    a--
                    b++
                }
                if (b - a + 1 >= steps.size) return null
                return steps.take(a) + steps.drop(b + 1)
            }
        }
        return null
    }

    private fun <T> oriented(list: List<T>, step: RouteStep) =
        if (step.reversed) list.asReversed() else list

    /** The route as one line of [lon, lat], each join vertex once. */
    fun line(steps: List<RouteStep>): List<List<Double>> {
        val out = mutableListOf<List<Double>>()
        steps.forEach { step ->
            val coords = oriented(byId.getValue(step.sectionId).coords, step)
            out += if (out.isEmpty()) coords else coords.drop(1)
        }
        return out
    }

    /** The sections' profiles joined in walking order, for the elevation slider. */
    fun profile(steps: List<RouteStep>): List<TrekElevationPoint> {
        val out = mutableListOf<TrekElevationPoint>()
        steps.forEach { step ->
            val samples = oriented(byId.getValue(step.sectionId).profile, step)
                .filter { it.size >= 3 }
                .map { TrekElevationPoint(lat = it[0], lon = it[1], elevationM = it[2].toInt()) }
            out += if (out.isEmpty()) samples else samples.drop(1)
        }
        return out
    }

    fun km(steps: List<RouteStep>): Double = steps.sumOf { byId.getValue(it.sectionId).km }

    fun climbM(steps: List<RouteStep>): Int = steps.sumOf {
        val s = byId.getValue(it.sectionId)
        if (it.reversed) s.descentM else s.climbM
    }

    /**
     * How far along the route a placement is: the length of the steps before its
     * section, plus how far into it. Null when the section is not on the route.
     * A side trip is walked out and back; its POIs count on the way out.
     */
    fun kmAlong(steps: List<RouteStep>, placement: PoiPlacement): Double? {
        var before = 0.0
        for (step in steps) {
            val s = byId.getValue(step.sectionId)
            if (step.sectionId == placement.section) {
                val into = placement.atKm.coerceIn(0.0, s.km)
                return before + if (step.reversed) s.km - into else into
            }
            before += s.km
        }
        return null
    }

    /** Sections the route does not use: drawn lighter, and tappable. */
    fun others(steps: List<RouteStep>): List<RouteSection> {
        val used = steps.mapTo(HashSet()) { it.sectionId }
        return routes.sections.filter { it.id !in used }
    }

    /**
     * The section of [candidates] nearest to a tap, if one is within [maxM] metres.
     * Measured on a flat projection around the tap, which at these distances is
     * exact to well under a metre.
     */
    fun nearestSection(lon: Double, lat: Double, maxM: Double, candidates: List<RouteSection>): String? {
        val mPerDegLat = 111_320.0
        val mPerDegLon = mPerDegLat * cos(lat * PI / 180.0)
        fun xy(p: List<Double>) = (p[0] - lon) * mPerDegLon to (p[1] - lat) * mPerDegLat

        var best: Pair<String, Double>? = null
        candidates.forEach { s ->
            s.coords.zipWithNext().forEach { (a, b) ->
                val (ax, ay) = xy(a)
                val (bx, by) = xy(b)
                val dx = bx - ax
                val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
                val d = hypot(ax + t * dx, ay + t * dy)
                if (d <= maxM && (best == null || d < best!!.second)) best = s.id to d
            }
        }
        return best?.first
    }

    /** Every section, as a GeoJSON geometry of its own, for map tiles and framing. */
    fun allLines(): List<List<List<Double>>> = routes.sections.map { it.coords }.filter { it.size >= 2 }

    /** [minLon, minLat, maxLon, maxLat] over every section. */
    fun boundingBox(): List<Double> {
        val points = routes.sections.flatMap { it.coords }.filter { it.size >= 2 }
        if (points.isEmpty()) return emptyList()
        return listOf(
            points.minOf { it[0] }, points.minOf { it[1] },
            points.maxOf { it[0] }, points.maxOf { it[1] }
        )
    }
}
