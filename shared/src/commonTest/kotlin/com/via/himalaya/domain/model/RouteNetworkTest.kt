package com.via.himalaya.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Kedarkantha's shape, from trek_routes/kedarkantha_0.json:
 *
 *   n1 -s1- n2 -s2- n3 -s3- n4 -s4- n5 -s5- n6 (summit)
 *            \       \      /        \      /
 *             \       s7   /          `-s8-'
 *              \      n7  /
 *               `--s6----'      (s6: the Hargaon way, n2 to n4)
 *
 * s7 is the spur to the Juda ka Talab camp, a dead end. Lengths are 1 km each
 * except s6 (3 km) and s7 (0.2 km), so distances read easily.
 */
class RouteNetworkTest {

    private val nodePos = mapOf(
        "n1" to (0.0 to 0.0), "n2" to (0.0 to 0.01), "n3" to (0.0 to 0.02),
        "n4" to (0.0 to 0.03), "n5" to (0.0 to 0.04), "n6" to (0.0 to 0.05),
        "n7" to (0.002 to 0.02)
    )

    private fun section(id: String, from: String, to: String, km: Double, climb: Int = 100, descent: Int = 0) =
        RouteSection(
            id = id, from = from, to = to, km = km, climbM = climb, descentM = descent,
            coords = listOf(nodePos.getValue(from), nodePos.getValue(to)).map { listOf(it.first, it.second) },
            profile = listOf(
                listOf(nodePos.getValue(from).second, nodePos.getValue(from).first, 1000.0, 0.0),
                listOf(nodePos.getValue(to).second, nodePos.getValue(to).first, 1100.0, km)
            )
        )

    private val routes = TrekRoutes(
        trekId = "test",
        nodes = nodePos.map { (id, p) -> RouteNode(id, p.first, p.second) },
        sections = listOf(
            section("s1", "n1", "n2", 1.0),
            section("s2", "n2", "n3", 1.0),
            section("s3", "n3", "n4", 1.0),
            section("s4", "n4", "n5", 1.0),
            section("s5", "n5", "n6", 1.0),
            section("s6", "n2", "n4", 3.0, climb = 300),
            section("s7", "n3", "n7", 0.2),
            // drawn the other way round in the file, summit to junction
            section("s8", "n6", "n5", 1.0, climb = 0, descent = 120)
        ),
        defaultRoute = listOf("s1", "s2", "s3", "s4", "s5"),
        pois = listOf(
            poi("hargaon", "s6", 1.0, 10),
            poi("camp", "s7", 0.2, 5),
            poi("base", "s4", 0.0, 50)
        )
    )
    private fun poi(id: String, section: String, atKm: Double, offsetM: Int) = RoutePoi(
        id = id, category = "camp_site", type = "camp_site", lat = 0.0, lon = 0.0,
        section = section, atKm = atKm, offsetM = offsetM
    )

    private fun at(id: String) = routes.pois.first { it.id == id }.let { it.section to it.atKm }

    private val net = RouteNetwork(routes)
    private val default = assertNotNull(net.defaultRoute())

    private fun ids(steps: List<RouteStep>?) = steps?.map { (if (it.reversed) "-" else "") + it.sectionId }

    @Test
    fun `the default chains end to end`() {
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), ids(default))
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5", "n6"), net.nodes(default))
        assertEquals(5.0, net.km(default))
    }

    @Test
    fun `a default whose first section is drawn backwards is turned round`() {
        val flipped = routes.copy(sections = routes.sections.map {
            if (it.id == "s1") section("s1", "n2", "n1", 1.0) else it
        })
        assertEquals(listOf("-s1", "s2", "s3", "s4", "s5"), ids(RouteNetwork(flipped).defaultRoute()))
    }

    @Test
    fun `a default that does not join up is null`() {
        assertNull(RouteNetwork(routes.copy(defaultRoute = listOf("s1", "s3"))).defaultRoute())
        assertNull(RouteNetwork(routes.copy(defaultRoute = listOf("s1", "nope"))).defaultRoute())
    }

    @Test
    fun `an alternative replaces the stretch between its ends`() {
        val hargaon = net.tap(default, "s6")
        assertEquals(listOf("s1", "s6", "s4", "s5"), ids(hargaon))
        assertEquals(6.0, net.km(hargaon!!))
    }

    @Test
    fun `either section of a two-section path swaps it back`() {
        val hargaon = net.tap(default, "s6")!!
        assertEquals(ids(default), ids(net.tap(hargaon, "s2")))
        assertEquals(ids(default), ids(net.tap(hargaon, "s3")))
    }

    @Test
    fun `an alternative drawn the other way is walked reversed`() {
        assertEquals(listOf("s1", "s2", "s3", "s4", "-s8"), ids(net.tap(default, "s8")))
    }

    @Test
    fun `a side trip is added out and back, and a second tap takes it off`() {
        val withCamp = net.tap(default, "s7")!!
        assertEquals(listOf("s1", "s2", "s7", "-s7", "s3", "s4", "s5"), ids(withCamp))
        assertEquals(ids(default), ids(net.tap(withCamp, "s7")))
    }

    @Test
    fun `a side trip off a path not on the route brings that path along`() {
        val hargaon = net.tap(default, "s6")!!
        val viaCamp = net.tap(hargaon, "s7")!!
        assertEquals(listOf("s1", "s2", "s7", "-s7", "-s2", "s6", "s4", "s5"), ids(viaCamp))
        assertEquals(ids(hargaon), ids(net.tap(viaCamp, "s7")))
    }

    @Test
    fun `a tap on the route's own path changes nothing`() {
        assertNull(net.tap(default, "s3"))
        assertNull(net.tap(net.tap(default, "s6")!!, "s6"))
    }

    @Test
    fun `a loop at one node is added and taken off`() {
        val withLoop = routes.copy(sections = routes.sections + RouteSection(
            id = "s9", from = "n4", to = "n4", km = 0.5,
            coords = listOf(listOf(0.0, 0.03), listOf(0.001, 0.031), listOf(0.0, 0.03))
        ))
        val n = RouteNetwork(withLoop)
        val added = n.tap(n.defaultRoute()!!, "s9")!!
        assertEquals(listOf("s1", "s2", "s3", "s9", "s4", "s5"), ids(added))
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), ids(n.tap(added, "s9")))
    }

    @Test
    fun `the line joins sections without repeating the join`() {
        val line = net.line(default)
        assertEquals(6, line.size)
        assertEquals(listOf(0.0, 0.0), line.first())
        assertEquals(listOf(0.0, 0.05), line.last())
        // reversed section s8 runs n5 -> n6 in the route
        assertEquals(listOf(0.0, 0.05), net.line(net.tap(default, "s8")!!).last())
    }

    @Test
    fun `the profile follows the route and climb uses descent on reversed sections`() {
        assertEquals(6, net.profile(default).size)
        assertEquals(500, net.climbM(default))
        assertEquals(400 + 120, net.climbM(net.tap(default, "s8")!!))
    }

    @Test
    fun `poi distance is the sections before it plus how far into it`() {
        fun km(steps: List<RouteStep>, id: String) = at(id).let { (s, k) -> net.kmAlong(steps, s, k) }
        assertEquals(3.0, km(default, "base"))
        assertNull(km(default, "hargaon"))
        val hargaon = net.tap(default, "s6")!!
        assertEquals(2.0, km(hargaon, "hargaon"))
        assertEquals(4.0, km(hargaon, "base"))
    }

    @Test
    fun `pois are placed along the route showing, and those on unused sections listed`() {
        val (onDefault, off) = com.via.himalaya.presentation.trekDetail.placePois(net, default)
        // the spur to the camp is not on the default either, until it is tapped
        assertEquals(setOf("hargaon", "camp"), off)
        assertEquals(3.0, onDefault.first { it.id == "base" }.distAlongKm)
        val (withCamp, off1) = com.via.himalaya.presentation.trekDetail.placePois(net, net.tap(default, "s7")!!)
        assertEquals(setOf("hargaon"), off1)
        assertEquals(2.2, withCamp.first { it.id == "camp" }.distAlongKm)
        val (onHargaon, off2) = com.via.himalaya.presentation.trekDetail.placePois(net, net.tap(default, "s6")!!)
        assertEquals(setOf("camp"), off2)
        assertEquals(2.0, onHargaon.first { it.id == "hargaon" }.distAlongKm)
    }

    @Test
    fun `others are the sections the route does not use`() {
        assertEquals(listOf("s6", "s7", "s8"), net.others(default).map { it.id })
    }

    @Test
    fun `a tap finds the nearest light section within reach`() {
        val others = net.others(default)
        // beside s7, which runs east from n3
        assertEquals("s7", net.nearestSection(0.001, 0.0201, 50.0, others))
        assertNull(net.nearestSection(0.01, 0.0201, 50.0, others))
    }

    @Test
    fun `the real file decodes`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<TrekRoutes>(
            """{"trek_id":"k","generated_at":"2026-11-17T10:00:00+00:00","nodes":[{"id":"n1","lon":78.1,"lat":31.0,"name":null}],
               "sections":[{"id":"s1","from":"n1","to":"n2","km":1.373,"climb_m":339,"descent_m":0,
               "coords":[[78.19,31.07],[78.18,31.06]],"profile":[[31.07,78.19,1966,0.0],[31.06,78.18,2305,1.373]]}],
               "default":["s1"],"pois":[{"id":"node/1","category":"water","type":"spring","name":"Spring",
               "lat":31.07,"lon":78.19,"section":"s1","at_km":0.5,"offset_m":12,"off_route":"start",
               "ele_m":2100.0,"tags":{"name":"Spring"},"later":true}],"future":1}"""
        )
        assertEquals(listOf("s1"), decoded.defaultRoute)
        assertEquals(339, decoded.sections[0].climbM)
        val poi = decoded.pois.single()
        assertEquals("s1", poi.section)
        assertEquals(0.5, poi.atKm)
        assertEquals("start", poi.offRoute)
        assertEquals("Spring", poi.tags["name"])
    }
}
