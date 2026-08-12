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
import androidx.compose.ui.input.pointer.pointerInput
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
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
import com.via.himalaya.util.PermissionUtil
import com.via.himalaya.ui.components.ElevationSlider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import com.via.himalaya.ui.components.TrekMap

private const val TAG = "TrekDetailScreenTag"

@Composable
fun TrekDetailScreenRoot(
    viewModel: TrekDetailViewModel,
    trekId: String,
    coordinateUrl: String,
    onBackClick: () -> Unit = {},
    onPlanClick: () -> Unit = {}
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
        onPlanClick = onPlanClick,
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
    onPlanClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current
    var selectedPoi by remember { mutableStateOf<TrekPoi?>(null) }

    // Empty for treks the backend has not profiled yet, and for treks downloaded
    // before the Room column existed. Both cases mean the same thing: no slider.
    val elevationPoints = state.trek?.elevationProfile.orEmpty()

    // Total ascent, summed from the profile we already ship - no schema or API
    // change needed. This is the number that separates 25 flat kilometres from
    // 25 hard ones, which max elevation on its own cannot say.
    val totalClimbM = remember(elevationPoints) {
        if (elevationPoints.size < 2) null
        else elevationPoints
            .zipWithNext { a, b -> (b.elevationM - a.elevationM).coerceAtLeast(0) }
            .sum()
    }
    var elevationIndex by remember(elevationPoints) { mutableIntStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }

    // The slider has to stop where the detail sheet starts, and that sheet is as
    // tall as its contents - so measure it rather than guessing a height that
    // would drift the moment a line of text wraps.
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val sheetHeight = with(LocalDensity.current) { sheetHeightPx.toDp() }

    // Hoisted out of the map block so the elevation slider can drive the camera
    // too. Bounds framing lives inside TrekMap, which every screen shares.
    val mapViewportState = rememberMapViewportState()

    // Follow the trekker while scrubbing. Centre only - zoom, pitch and bearing
    // stay as the user left them, and an animated ease would lag behind a drag
    // that keeps issuing new targets.
    LaunchedEffect(isScrubbing, elevationIndex) {
        if (!isScrubbing) return@LaunchedEffect
        elevationPoints.getOrNull(elevationIndex)?.let { point ->
            mapViewportState.setCameraOptions(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(point.lon, point.lat))
                    .build()
            )
        }
    }

    Box (
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {

        if (state.geoData != null && state.trek != null) {
            val geoJsonString = state.geoData?.geometry?.toGeoJsonString()
            val boundingBox = state.trek?.boundingBox

            geoJsonString?.let {
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

                TrekMap(
                    geoJson = geoJsonString,
                    boundingBox = boundingBox,
                    mapViewportState = mapViewportState,
                    modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
                    // A card anchored to a marker has to go when the map is
                    // tapped elsewhere, and when the marker slides out from
                    // under it on a pan.
                    onMapTap = { selectedPoi = null },
                    onMapPan = { selectedPoi = null }
                ) {
                    // Scrubbing and browsing are mutually exclusive: leaving the
                    // pins up would bury the trekker among them, and the whole
                    // point of the gesture is to follow one marker.
                    if (!isScrubbing) {
                        PoiMarkers(
                            pois = state.pois,
                            onPoiClick = { selectedPoi = it }
                        )
                    } else {
                        elevationPoints.getOrNull(elevationIndex)?.let { point ->
                            val trekkerIcon = rememberIconImage(
                                key = "trekker",
                                painter = painterResource(R.drawable.ic_trekker)
                            )
                            PointAnnotation(
                                point = Point.fromLngLat(point.lon, point.lat)
                            ) {
                                iconImage = trekkerIcon
                            }
                        }
                    }

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
        
        // Runs from the top of the screen down to the detail sheet. Declared
        // after the map so it sits above it and wins the touch, and before the
        // sheet so the sheet still draws over the bottom of the track.
        if (elevationPoints.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, bottom = sheetHeight + 8.dp, end = 4.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                ElevationSlider(
                    points = elevationPoints,
                    index = elevationIndex,
                    isEngaged = isScrubbing,
                    onIndexChange = { elevationIndex = it },
                    onEngagedChange = { engaged ->
                        isScrubbing = engaged
                        // A tapped POI card would otherwise sit over the trail
                        // the user is now scrubbing along.
                        if (engaged) selectedPoi = null
                    },
                    // Full width: the slider pins its own graph to the right edge
                    // and uses the rest of the row to lay the readout out.
                    modifier = Modifier.fillMaxSize()
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
                .onSizeChanged { sheetHeightPx = it.height }
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

                // Replaces a three-column box with dividers and a background.
                // Two or three facts do not need that furniture, and dropping it
                // gave back around 90dp - which the elevation slider inherits,
                // since its travel is bounded by the height of this sheet.
                //
                // Built from whatever is actually present: climb needs a profile,
                // and a trek without one still shows distance and max.
                val stats = buildList {
                    state.trek?.distance?.takeIf { it.isNotBlank() }?.let { add(it) }
                    totalClimbM?.let { add("↑ %,d m".format(it)) }
                    state.trek?.elevation?.takeIf { it.isNotBlank() }
                        // "4,283 m" alone reads as though it might be the climb.
                        ?.let { add("Max $it") }
                }
                if (stats.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(top = 10.dp),
                        text = stats.joinToString("   ·   "),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

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
                    // Side by side rather than stacked: a third full-width button
                    // would grow this sheet, and the sheet's height is what caps
                    // the elevation slider's travel.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SecondaryButton(
                            text = "Plan",
                            onClick = { onPlanClick() },
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = "Download",
                            onClick = { onDownloadHikeClick() },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
internal fun poiIconRes(category: String): Int = when (category) {
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
internal fun PoiDetailCard(
    poi: TrekPoi,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            // The card sits over the MapView, which is a plain Android view - with
            // nothing consuming here, taps and drags fall straight through to the
            // map and dismiss the card the user is trying to read.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
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
