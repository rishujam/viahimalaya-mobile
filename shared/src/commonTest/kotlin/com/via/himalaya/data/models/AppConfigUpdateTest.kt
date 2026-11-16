package com.via.himalaya.data.models

import com.via.himalaya.domain.model.AppConfig
import com.via.himalaya.domain.model.UpdateNeed
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppConfigUpdateTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(data: String) = json.decodeFromString<AppConfigDto>(data).toDomain()

    private val row = """{"app_update": {"min_version": 14, "latest_version": 16, "message": " Please update "}}"""

    @Test
    fun `no config means nobody is asked to update`() {
        assertEquals(UpdateNeed.NONE, AppConfig().updateNeed(1))
        assertEquals(UpdateNeed.NONE, parse("{}").updateNeed(1))
    }

    @Test
    fun `below min is required, between min and latest is optional, at latest is none`() {
        val config = parse(row)
        assertEquals(UpdateNeed.REQUIRED, config.updateNeed(13))
        assertEquals(UpdateNeed.OPTIONAL, config.updateNeed(14))
        assertEquals(UpdateNeed.OPTIONAL, config.updateNeed(15))
        assertEquals(UpdateNeed.NONE, config.updateNeed(16))
        assertEquals(UpdateNeed.NONE, config.updateNeed(17))
    }

    @Test
    fun `the message is trimmed and a blank one is dropped`() {
        assertEquals("Please update", parse(row).update?.message)
        assertNull(parse("""{"app_update": {"min_version": 14, "message": "  "}}""").update?.message)
    }

    @Test
    fun `either version on its own works`() {
        assertEquals(UpdateNeed.REQUIRED, parse("""{"app_update": {"min_version": 14}}""").updateNeed(13))
        assertEquals(UpdateNeed.OPTIONAL, parse("""{"app_update": {"latest_version": 14}}""").updateNeed(13))
    }

    @Test
    fun `a malformed version never blocks anyone`() {
        for (value in listOf("\"14\"", "14.5", "-1", "0", "null", "{}")) {
            val config = parse("""{"app_update": {"min_version": $value}}""")
            assertNull(config.update, "value $value")
            assertEquals(UpdateNeed.NONE, config.updateNeed(1), "value $value")
        }
    }

    @Test
    fun `app_update that is not an object is ignored and the rest still parses`() {
        val config = parse("""{"app_update": 14, "features": {"planner": true}}""")
        assertNull(config.update)
        assertEquals(mapOf("planner" to true), config.features)
    }

    @Test
    fun `a good version survives next to a bad one`() {
        val update = parse("""{"app_update": {"min_version": "x", "latest_version": 15}}""").update
        assertNotNull(update)
        assertNull(update.minVersion)
        assertEquals(15, update.latestVersion)
    }
}
