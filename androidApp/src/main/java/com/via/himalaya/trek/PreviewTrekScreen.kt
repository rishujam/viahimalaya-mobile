package com.via.himalaya.trek

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.ColorValue
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.compose.style.layers.generated.LineCapValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineJoinValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineLayer
import com.mapbox.maps.extension.compose.style.sources.GeoJSONData
import com.mapbox.maps.extension.compose.style.sources.generated.rememberGeoJsonSourceState
import com.via.himalaya.resources.ResourceLoader

@Composable
fun PreviewTrekScreen() {
    val context = LocalContext.current
    var geoJsonString by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            geoJsonString = ResourceLoader.loadAbcJsonString()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val trailSource = rememberGeoJsonSourceState(sourceId = "abc-trail-source")
    if (geoJsonString.isNotEmpty()) {
        trailSource.data = GeoJSONData(geoJsonString)
    }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(83.885, 28.481))
            zoom(11.0)
            pitch(45.0)
            bearing(0.0)
        }
    }

    MapboxMap(
        modifier = Modifier.fillMaxSize(),
        mapViewportState = mapViewportState,
        style = {
            MapStyle(
                style = Style.SATELLITE_STREETS
            )
        }
    ) {
        // Draw the path directly inside the map using Style Layers
        LineLayer(
            layerId = "abc-trail-line-layer",
            sourceState = trailSource
        ) {
            // Style properties defined using Mapbox values
            lineColor = ColorValue(Color(0xFF4285F4)) // Your branding green tone
            lineWidth = DoubleValue(5.0)              // Visible thickness on terrain
            lineJoin = LineJoinValue.ROUND
            lineCap = LineCapValue.ROUND
        }
    }
}
