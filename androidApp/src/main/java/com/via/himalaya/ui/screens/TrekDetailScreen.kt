package com.via.himalaya.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.via.himalaya.domain.model.TrekDetail
import com.via.himalaya.domain.model.toGeoJsonString
import com.via.himalaya.presentation.trekDetail.TrekDetailScreenUIState
import com.via.himalaya.presentation.trekDetail.TrekDetailViewModel
import com.via.himalaya.ui.components.PrimaryButton
import com.via.himalaya.ui.components.SecondaryButton
import org.koin.androidx.compose.koinViewModel

@Composable
fun TrekDetailScreenRoot(
    viewModel: TrekDetailViewModel = koinViewModel(),
    trekId: String,
    coordinateUrl: String,
    onBackClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    viewModel.getTrek(trekId)
    viewModel.getCoordinates(coordinateUrl)
    TrekDetailScreen(
        state = state,
        onBackClick = onBackClick
    )
}

@Composable
fun TrekDetailScreen(
    state: TrekDetailScreenUIState,
    onBackClick: () -> Unit = {}
) {
    Box (
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {

        if (state.geoData != null && state.trek != null) {
            val geoJsonString = state.geoData?.geometry?.toGeoJsonString()
            val boundingBox = state.trek?.boundingBox
            
            val trailSource = rememberGeoJsonSourceState(sourceId = "trek-trail-source")
            geoJsonString?.let {
                trailSource.data = GeoJSONData(geoJsonString)

                val mapViewportState = rememberMapViewportState {
                    setCameraOptions {
                        // Use bounding box to calculate center and appropriate zoom
                        if (boundingBox?.size != null && boundingBox.size >= 4) {
                            val centerLng = (boundingBox[0] + boundingBox[2]) / 2
                            val centerLat = (boundingBox[1] + boundingBox[3]) / 2
                            center(Point.fromLngLat(centerLng, centerLat))

                            // Calculate zoom level based on bounding box size
                            val lngDiff = kotlin.math.abs(boundingBox[2] - boundingBox[0])
                            val latDiff = kotlin.math.abs(boundingBox[3] - boundingBox[1])
                            val maxDiff = maxOf(lngDiff, latDiff)

                            // Adjust zoom based on bounding box size
                            val zoomLevel = when {
                                maxDiff > 1.0 -> 9.0
                                maxDiff > 0.5 -> 10.0
                                maxDiff > 0.2 -> 11.0
                                maxDiff > 0.1 -> 12.0
                                else -> 13.0
                            }
                            zoom(zoomLevel)
                        }
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
                    LineLayer(
                        layerId = "trek-trail-line-layer",
                        sourceState = trailSource
                    ) {
                        lineColor = ColorValue(Color(0xFF4285F4))
                        lineWidth = DoubleValue(5.0)
                        lineJoin = LineJoinValue.ROUND
                        lineCap = LineCapValue.ROUND
                    }
                }
            }
        }
        
        // Back button - positioned at top left
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topEnd = 14.dp, topStart = 14.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    modifier = Modifier.padding(top = 20.dp),
                    text = state.trek?.name.orEmpty(),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = state.trek?.location.orEmpty(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onPrimary),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DISTANCE",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.trek?.distance.orEmpty(),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Column(
                        modifier = Modifier
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "DURATION",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "4h 30m",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                    Column(
                        modifier = Modifier
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ELEVATION",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.trek?.elevation.orEmpty(),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.fillMaxWidth().height(20.dp))
                PrimaryButton(
                    text = "Start Hike",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.fillMaxWidth().height(16.dp))
                SecondaryButton(
                    text = "Download for offline",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    trailingText = "38 MB"
                )
                Spacer(modifier = Modifier.fillMaxWidth().height(20.dp))
            }
        }
    }
}


@Preview
@Composable
fun TrekDetailScreenPreview() {
    TrekDetailScreen(
        state = TrekDetailScreenUIState(
            trek = TrekDetail(
                id = "x",
                name = "Triund Trek",
                location = "Dharamshala, Himachal Pradesh",
                distance = "9 Km",
                coordinateUrl = "xyz.com",
                elevation = "800 m",
                boundingBox = emptyList()
            )
        ),
        onBackClick = {}
    )
}
