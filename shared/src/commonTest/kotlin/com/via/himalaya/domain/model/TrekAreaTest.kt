package com.via.himalaya.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The same cases, with the same numbers, as the server's tests of
 * isInsideTrekArea - the two have to agree, or the app lets a user place a pin
 * the server then refuses.
 */
class TrekAreaTest {

    // Goechala: [minLon, minLat, maxLon, maxLat], as stored on the trek.
    private val goechala = listOf(88.160247, 27.3786358, 88.2213656, 27.5812213)

    private fun metresPerDegreeLon(lat: Double) = 111_320.0 * cos(lat * PI / 180.0)

    @Test
    fun insideTheBox() {
        assertTrue(TrekArea.contains(27.45, 88.19, goechala))
    }

    @Test
    fun onTheEdge() {
        assertTrue(TrekArea.contains(27.4, 88.160247, goechala))
    }

    @Test
    fun marginAppliesInEveryDirection() {
        val lat = 27.45
        // west
        assertTrue(TrekArea.contains(lat, 88.160247 - 500 / metresPerDegreeLon(lat), goechala))
        assertFalse(TrekArea.contains(lat, 88.160247 - 1500 / metresPerDegreeLon(lat), goechala))
        // east
        assertTrue(TrekArea.contains(lat, 88.2213656 + 900 / metresPerDegreeLon(lat), goechala))
        assertFalse(TrekArea.contains(lat, 88.2213656 + 1100 / metresPerDegreeLon(lat), goechala))
        // north
        assertTrue(TrekArea.contains(27.5812213 + 900 / 111_320.0, 88.19, goechala))
        assertFalse(TrekArea.contains(27.5812213 + 1100 / 111_320.0, 88.19, goechala))
        // south
        assertTrue(TrekArea.contains(27.3786358 - 900 / 111_320.0, 88.19, goechala))
        assertFalse(TrekArea.contains(27.3786358 - 1100 / 111_320.0, 88.19, goechala))
    }

    @Test
    fun somewhereElseEntirely() {
        assertFalse(TrekArea.contains(28.6139, 77.209, goechala)) // Delhi
        assertFalse(TrekArea.contains(0.0, 0.0, goechala))
    }

    @Test
    fun zeroMarginMeansExactlyTheBox() {
        assertFalse(TrekArea.contains(27.45, 88.160247 - 0.0001, goechala, paddingM = 0.0))
        assertTrue(TrekArea.contains(27.45, 88.17, goechala, paddingM = 0.0))
    }

    @Test
    fun aTrekWithoutAUsableBoxContainsNothing() {
        assertFalse(TrekArea.contains(27.45, 88.19, emptyList()))
        assertFalse(TrekArea.contains(27.45, 88.19, listOf(88.1, 27.3)))
    }
}
