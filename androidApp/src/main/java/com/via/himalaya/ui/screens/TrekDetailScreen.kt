package com.via.himalaya.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import com.google.android.gms.common.api.ResolvableApiException
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.style.ColorValue
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.compose.style.layers.generated.LineCapValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineJoinValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineLayer
import com.mapbox.maps.extension.compose.style.sources.GeoJSONData
import com.mapbox.maps.extension.compose.style.sources.generated.rememberGeoJsonSourceState
import com.mapbox.maps.extension.compose.MapboxMapScope
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import androidx.compose.ui.res.painterResource
import androidx.browser.customtabs.CustomTabsIntent
import com.via.himalaya.R
import com.via.himalaya.data.models.readableType
import com.via.himalaya.data.models.PoiCategory
import com.via.himalaya.data.models.TrekPoi
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.domain.model.LocationResponse
import com.via.himalaya.domain.model.toGeoJsonString
import com.via.himalaya.presentation.trekDetail.TrekDetailScreenUIState
import com.via.himalaya.presentation.trekDetail.TrekDetailViewModel
import com.via.himalaya.service.TrekDownloadService
import com.via.himalaya.ui.components.PrimaryButton
import com.via.himalaya.ui.components.SecondaryButton
import com.via.himalaya.util.Constants
import com.via.himalaya.util.PermissionUtil

private const val TAG = "TrekDetailScreenTag"

@Composable
fun TrekDetailScreenRoot(
    viewModel: TrekDetailViewModel,
    trekId: String,
    coordinateUrl: String,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if(isGranted) {
            TrekDownloadService.startService(
                context = context,
                trekId = trekId,
                trekName = state.trek?.name.orEmpty()
            )
        } else {
            Toast.makeText(
                context,
                "Notification permission is required to download a trek",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val activity = LocalActivity.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        activity?.let {
            val showRationale = shouldShowRequestPermissionRationale(activity, PermissionUtil.PERMISSION_LOCATION_PRECISE)
            println("$TAG, showRationale location: $showRationale")
            if(!showRationale && (!hasFineLocation || !hasCoarseLocation)) {
                showPermissionSettingsDialog = true
            }
        }
        println("$TAG, locationPermissionLauncher result")
        if(hasCoarseLocation && hasFineLocation) {
            println("$TAG, location permission granted getting initial location")
            viewModel.getInitialLocation()
        } else {
            println("$TAG, location permission denied ")
        }
    }

    // Handle error toast
    LaunchedEffect(state.errorToast) {
        state.errorToast?.let { errorMessage ->
            snackBarHostState.showSnackbar(
                message = errorMessage,
                withDismissAction = true
            )
            viewModel.clearErrorToast()
        }
    }

    LaunchedEffect(Unit) {
        println("$TAG, calling initial data setup onetime")
        viewModel.setInitialData(coordinateUrl, trekId)
        if(PermissionUtil.hasLocationPermission(context)) {
            println("$TAG, has location permission getting initial location")
            viewModel.getInitialLocation()
        } else {
            println("$TAG, permission not granted state found")
            println("$TAG, launching location permission")
            locationPermissionLauncher.launch(
                arrayOf(
                    PermissionUtil.PERMISSION_LOCATION,
                    PermissionUtil.PERMISSION_LOCATION_PRECISE
                )
            )
        }
    }

    LaunchedEffect(state.initialLocation is LocationResponse.SettingDisabled) {
        if(state.initialLocation is LocationResponse.SettingDisabled) {
            println("$TAG, setting disabled state found")
            val ex = (state.initialLocation as? LocationResponse.SettingDisabled)?.exception as? ResolvableApiException
            activity?.let {
                println("$TAG, launching location setting")
                ex?.startResolutionForResult(activity, 100)
            }
        }
    }
    
    TrekDetailScreen(
        state = state,
        onBackClick = onBackClick,
        onStartHike = {
            if(PermissionUtil.hasLocationPermission(context)) {
                viewModel.startTrekking(trekId)
            } else {
                Toast.makeText(
                    context,
                    "Location permission not granted",
                    Toast.LENGTH_SHORT
                ).show()
                locationPermissionLauncher.launch(
                    arrayOf(
                        PermissionUtil.PERMISSION_LOCATION,
                        PermissionUtil.PERMISSION_LOCATION_PRECISE
                    )
                )
            }
        },
        onStopHike = { viewModel.stopTrekking() },
        onDownloadHikeClick = {
            viewModel.validateAndStartDownload { trek ->
                if(PermissionUtil.hasNotificationPermission(context)) {
                    TrekDownloadService.startService(
                        context = context,
                        trekId = trek.id,
                        trekName = trek.name
                    )
                } else {
                    notificationPermissionLauncher.launch(
                        PermissionUtil.PERMISSION_NOTIFICATION
                    )
                }
            }
        },
        snackbarHostState = snackBarHostState
    )

    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            title = { Text(text = "Location permission required") },
            text = {
                Text(
                    text = "If you want to navigate a hike, you need to grant location permission."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionSettingsDialog = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Text(text = "Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionSettingsDialog = false }
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

@Composable
fun TrekDetailScreen(
    state: TrekDetailScreenUIState,
    onBackClick: () -> Unit = {},
    onStartHike: () -> Unit = {},
    onStopHike: () -> Unit = {},
    onDownloadHikeClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current
    var selectedPoi by remember { mutableStateOf<TrekPoi?>(null) }

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

                LaunchedEffect(state.initialLocation, state.isNearTrekStart) {
                    if (state.isNearTrekStart && state.initialLocation is LocationResponse.Location) {
                        (state.initialLocation as? LocationResponse.Location)?.loc?.let { location ->
                            mapViewportState.setCameraOptions(
                                CameraOptions.Builder()
                                    .center(Point.fromLngLat(location.lon, location.lat))
                                    .zoom(16.0)
                                    .pitch(45.0)
                                    .bearing(0.0)
                                    .build()
                            )
                        }
                    }
                }

                MapboxMap(
                    modifier = Modifier.fillMaxSize(),
                    mapViewportState = mapViewportState,
                    style = {
                        MapStyle(
                            style = Constants.Map.STYLE_URI
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

                    PoiMarkers(
                        pois = state.pois,
                        onPoiClick = { selectedPoi = it }
                    )

                    if (state.isNearTrekStart) {
                        val location = state.liveLocation
                        location?.let {
                            CircleAnnotation(
                                point = Point.fromLngLat(location.lon, location.lat)
                            ) {
                                circleRadius = 10.0
                                circleColor = Color(0xFF4285F4)
                                circleStrokeWidth = 3.0
                                circleStrokeColor = Color.White
                            }
                        }
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
        
        // Tapped POI takes the top slot, pushing the trekking-area banner aside.
        selectedPoi?.let { poi ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                PoiDetailCard(
                    poi = poi,
                    onDismiss = { selectedPoi = null }
                )
            }
        }

        // Show message when NOT trekking and not in bounding box
        if (!state.isTrekking && !state.isNearTrekStart && selectedPoi == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF9800))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "You are not in the trekking area",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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

                // Hidden entirely when the trek has no write-up, rather than
                // showing a dead link.
                state.trek?.detailsUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    Text(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { openDetailsLink(context, url) },
                        text = "View details",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1A73E8),
                        textDecoration = TextDecoration.Underline
                    )
                }
                if(!state.isTrekking) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.fillMaxWidth().height(20.dp))
                PrimaryButton(
                    text = if (state.isTrekking) "Stop Hike" else "Start Hike",
                    onClick = {
                        if (state.isTrekking) {
                            onStopHike()
                        } else {
                            onStartHike()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if(!state.isTrekking) {
                    Spacer(modifier = Modifier.fillMaxWidth().height(16.dp))
                    SecondaryButton(
                        text = "Download for offline",
                        onClick = { onDownloadHikeClick() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.fillMaxWidth().height(20.dp))
            }
        }
        
        // Snackbar for error messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(16.dp)
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
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
        onBackClick = {},
        onDownloadHikeClick = {},
        snackbarHostState = remember { SnackbarHostState() }
    )
}



/** Marker drawable per category, falling back to the question mark. */
private fun poiIconRes(category: String): Int = when (category) {
    PoiCategory.WATER -> R.drawable.ic_poi_water
    PoiCategory.HOT_SPRING -> R.drawable.ic_poi_hot_spring
    PoiCategory.WATERFALL -> R.drawable.ic_poi_waterfall
    PoiCategory.LAKE -> R.drawable.ic_poi_lake
    PoiCategory.CAMP_SITE -> R.drawable.ic_poi_camp_site
    PoiCategory.STAY -> R.drawable.ic_poi_stay
    PoiCategory.SHELTER -> R.drawable.ic_poi_shelter
    PoiCategory.LANDMARK -> R.drawable.ic_poi_landmark
    PoiCategory.WATER_CROSSING -> R.drawable.ic_poi_water_crossing
    else -> R.drawable.ic_poi_other
}

/**
 * One tappable marker per POI.
 *
 * Rendered in PoiCategory.DRAW_ORDER so a water crossing is never buried under a
 * guest house. Icons are keyed by category, so Mapbox uploads ten images to the
 * style rather than one per marker.
 */
@Composable
private fun MapboxMapScope.PoiMarkers(
    pois: List<TrekPoi>,
    onPoiClick: (TrekPoi) -> Unit
) {
    if (pois.isEmpty()) return

    PoiCategory.DRAW_ORDER.forEach { category ->
        val inCategory = pois.filter { it.category == category }
        if (inCategory.isEmpty()) return@forEach

        val icon = rememberIconImage(
            key = category,
            painter = painterResource(poiIconRes(category))
        )

        inCategory.forEach { poi ->
            PointAnnotation(point = Point.fromLngLat(poi.lon, poi.lat)) {
                iconImage = icon
                // Things beyond the trailhead are context, not part of the walk.
                iconOpacity = if (poi.isOnRoute) 1.0 else 0.55
                interactionsState.onClicked {
                    onPoiClick(poi)
                    true
                }
            }
        }
    }
}

/** Detail card shown when a marker is tapped. */
@Composable
private fun PoiDetailCard(
    poi: TrekPoi,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(poiIconRes(poi.category)),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = poi.name ?: PoiCategory.label(poi.category),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // The exact OSM kind - a water pin still says spring vs well.
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = "${PoiCategory.label(poi.category)} \u00B7 ${poi.readableType()}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val position = if (poi.isOnRoute) {
                    "km ${poi.distAlongKm} along \u00B7 ${poi.offsetM} m off trail"
                } else {
                    "Near the trailhead \u00B7 ${poi.offsetM} m off trail"
                }
                Text(
                    modifier = Modifier.padding(top = 6.dp),
                    text = position,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                poi.eleM?.let {
                    Text(
                        modifier = Modifier.padding(top = 2.dp),
                        text = "${it.toInt()} m",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Anything useful the mapper recorded: phone, capacity, wifi...
                val extras = poi.tags.filterKeys { it != "name" && it != "name:en" }
                if (extras.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    extras.forEach { (key, value) ->
                        Text(
                            text = "${key.replace('_', ' ')}: $value",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (poi.approxCenter) {
                    Text(
                        modifier = Modifier.padding(top = 6.dp),
                        text = "Approximate position",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u2715",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Opens the trek write-up in a Custom Tab, so the user stays inside the app's
 * task and back returns straight to the map - it matters when the destination
 * is a funnel page. Falls back to any installed browser.
 */
private fun openDetailsLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e2: Exception) {
            Toast.makeText(context, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }
}
