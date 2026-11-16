package com.via.himalaya.data.models

import com.via.himalaya.domain.model.Feature
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppConfigFeaturesTest {

    // The same settings HttpClientFactory gives the API client.
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(data: String) = json.decodeFromString<AppConfigDto>(data).toDomain()

    @Test
    fun `no features section falls back to the in-app default`() {
        val config = parse("""{}""")
        assertEquals(Feature.PLANNER.default, config.isEnabled(Feature.PLANNER))
    }

    @Test
    fun `the planner default is off`() {
        assertFalse(Feature.PLANNER.default)
    }

    @Test
    fun `a switch the server sends wins over the default`() {
        assertTrue(parse("""{"features": {"planner": true}}""").isEnabled(Feature.PLANNER))
        assertFalse(parse("""{"features": {"planner": false}}""").isEnabled(Feature.PLANNER))
    }

    @Test
    fun `a missing key falls back to the default`() {
        val config = parse("""{"features": {"something_else": true}}""")
        assertEquals(Feature.PLANNER.default, config.isEnabled(Feature.PLANNER))
    }

    @Test
    fun `a value that is not a boolean is ignored, not fatal`() {
        for (value in listOf("\"true\"", "1", "null", "{}", "[]")) {
            val config = parse("""{"features": {"planner": $value}}""")
            assertEquals(Feature.PLANNER.default, config.isEnabled(Feature.PLANNER), "value $value")
        }
    }

    @Test
    fun `features that is not an object is ignored and the banner still parses`() {
        val config = parse(
            """{"features": "planner",
               "banner": {"title": "t", "description": "d", "action": "REQUEST_TREK_DIALOG"}}"""
        )
        assertEquals(Feature.PLANNER.default, config.isEnabled(Feature.PLANNER))
        assertNotNull(config.banner)
    }

    @Test
    fun `unknown switches from a newer server do not break anything`() {
        val config = parse("""{"features": {"planner": true, "routes": true}}""")
        assertTrue(config.isEnabled(Feature.PLANNER))
        assertEquals(mapOf("planner" to true, "routes" to true), config.features)
    }
}
