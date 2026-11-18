package com.via.himalaya.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.DisposableMapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.MapboxMapScope
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.style.ColorValue
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.compose.style.layers.generated.LineCapValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineJoinValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineLayer
import com.mapbox.maps.extension.compose.style.sources.GeoJSONData
import com.mapbox.maps.extension.compose.style.sources.generated.rememberGeoJsonSourceState
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.via.himalaya.util.Constants
import kotlin.math.pow

private const val EMPTY_COLLECTION = """{"type":"FeatureCollection","features":[]}"""

private val TRAIL_COLOR = Color(0xFF4285F4)

/** The paths the route does not use: the same blue, thinner and see-through. */
private val OTHER_PATH_COLOR = Color(0xFF4285F4)
private const val OTHER_PATH_OPACITY = 0.45

/**
 * How near a path a tap counts as on it. A finger covers about this much, and a
 * thin line is hard to hit dead on.
 */
private const val TAP_TOLERANCE_DP = 20.0

/** Ground metres per screen dp at zoom 0 on the equator: 512-dp world tiles. */
private const val METRES_PER_DP_AT_ZOOM_0 = 78_271.517

/**
 * The trek map every screen shares: satellite style, the trail drawn as one
 * line, and a camera framed on the trek's bounds.
 *
 * Markers are the caller's business - detail draws POIs and the scrubbing
 * trekker, planning draws campsites and day-coloured segments - so [content]
 * takes whatever annotations that screen needs. Only the parts that are
 * genuinely identical live here; anything a screen wants to vary is a
 * parameter, not a flag.
 *
 * @param otherPathsGeoJson the sections the route does not use, drawn under the
 *   trail in a lighter line. Null draws none.
 * @param onMapTap fires for taps that no marker consumed, i.e. taps on bare map,
 *   with where it landed and how many metres around it count as a hit at the
 *   current zoom.
 * @param onMapPan fires when the user starts dragging. Both exist because a card
 *   anchored to a marker has to go away when that marker moves or loses focus.
 */
@Composable
fun TrekMap(
    geoJson: String,
    boundingBox: List<Double>?,
    mapViewportState: MapViewportState,
    modifier: Modifier = Modifier,
    otherPathsGeoJson: String? = null,
    onMapTap: (point: Point, toleranceM: Double) -> Unit = { _, _ -> },
    onMapPan: () -> Unit = {},
    content: @Composable MapboxMapScope.() -> Unit = {}
) {
    val trailSource = rememberGeoJsonSourceState(sourceId = "trek-trail-source")
    trailSource.data = GeoJSONData(geoJson)
    val otherPathsSource = rememberGeoJsonSourceState(sourceId = "trek-other-paths-source")
    otherPathsSource.data = GeoJSONData(otherPathsGeoJson ?: EMPTY_COLLECTION)

    // Frames the trek once its bounds arrive. rememberMapViewportState's own
    // initial-camera block runs on first composition, which is before the trek
    // has loaded, so the framing has to happen here instead.
    LaunchedEffect(boundingBox) {
        if (boundingBox == null || boundingBox.size < 4) return@LaunchedEffect

        val lngDiff = kotlin.math.abs(boundingBox[2] - boundingBox[0])
        val latDiff = kotlin.math.abs(boundingBox[3] - boundingBox[1])
        val zoomLevel = when (maxOf(lngDiff, latDiff)) {
            in 1.0..Double.MAX_VALUE -> 9.0
            in 0.5..1.0 -> 10.0
            in 0.2..0.5 -> 11.0
            in 0.1..0.2 -> 12.0
            else -> 13.0
        }
        mapViewportState.setCameraOptions(
            CameraOptions.Builder()
                .center(
                    Point.fromLngLat(
                        (boundingBox[0] + boundingBox[2]) / 2,
                        (boundingBox[1] + boundingBox[3]) / 2
                    )
                )
                .zoom(zoomLevel)
                .pitch(45.0)
                .bearing(0.0)
                .build()
        )
    }

    MapboxMap(
        modifier = modifier,
        mapViewportState = mapViewportState,
        // Markers consume their own taps, so anything arriving here is a tap on
        // bare map. Returns false: we observe the click without swallowing it.
        onMapClickListener = { point ->
            val zoom = mapViewportState.cameraState?.zoom ?: 13.0
            val metresPerDp = METRES_PER_DP_AT_ZOOM_0 *
                kotlin.math.cos(Math.toRadians(point.latitude())) / 2.0.pow(zoom)
            onMapTap(point, TAP_TOLERANCE_DP * metresPerDp)
            false
        },
        style = { MapStyle(style = Constants.Map.STYLE_URI) }
    ) {
        DisposableMapEffect(Unit) { mapView ->
            val moveListener = object : OnMoveListener {
                override fun onMoveBegin(detector: MoveGestureDetector) = onMapPan()
                override fun onMove(detector: MoveGestureDetector) = false
                override fun onMoveEnd(detector: MoveGestureDetector) = Unit
            }
            mapView.gestures.addOnMoveListener(moveListener)
            onDispose { mapView.gestures.removeOnMoveListener(moveListener) }
        }

        // Under the trail, so where a path leaves the route the route stays on top.
        LineLayer(
            layerId = "trek-other-paths-layer",
            sourceState = otherPathsSource
        ) {
            lineColor = ColorValue(OTHER_PATH_COLOR)
            lineOpacity = DoubleValue(OTHER_PATH_OPACITY)
            lineWidth = DoubleValue(3.0)
            lineJoin = LineJoinValue.ROUND
            lineCap = LineCapValue.ROUND
        }

        LineLayer(
            layerId = "trek-trail-line-layer",
            sourceState = trailSource
        ) {
            lineColor = ColorValue(TRAIL_COLOR)
            lineWidth = DoubleValue(5.0)
            lineJoin = LineJoinValue.ROUND
            lineCap = LineCapValue.ROUND
        }

        content()
    }
}
