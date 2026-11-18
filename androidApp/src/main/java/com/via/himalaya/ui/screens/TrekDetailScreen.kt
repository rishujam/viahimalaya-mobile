package com.via.himalaya.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.style.TextAlign
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
import com.via.himalaya.presentation.trekDetail.lineGeoJson
import com.via.himalaya.presentation.trekDetail.sectionsGeoJson
import com.via.himalaya.presentation.trekDetail.TrekDetailScreenUIState
import com.via.himalaya.presentation.trekDetail.TrekDetailScreenUIEvent
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
import android.content.ActivityNotFoundException
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.saveable.rememberSaveable
import com.via.himalaya.domain.model.CustomPoiRules
import com.via.himalaya.presentation.addPoi.AddPoiUiState
import com.via.himalaya.presentation.addPoi.AddPoiViewModel
import com.via.himalaya.ui.components.AddPlaceButton
import com.via.himalaya.ui.components.AreaPrompt
import com.via.himalaya.ui.components.AddPoiActions
import com.via.himalaya.ui.components.AddPoiDialog
import com.via.himalaya.ui.components.PoiLocationPicker
import com.via.himalaya.util.CameraFiles
import org.koin.androidx.compose.koinViewModel

private const val TAG = "TrekDetailScreenTag"

/**
 * The map sits this far above the bottom of the screen, so the detail sheet does
 * not cover its logo. The pin picker has to use the same figure: the map's centre
 * is what the pin stands on, and it moves when this does.
 */
private val MAP_BOTTOM_PADDING = 64.dp

@Composable
fun TrekDetailScreenRoot(
    viewModel: TrekDetailViewModel,
    trekId: String,
    onBackClick: () -> Unit = {},
    onPlanClick: () -> Unit = {},
    addPoiViewModel: AddPoiViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val addPoiState by addPoiViewModel.state.collectAsState()
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
            viewModel.onEvent(TrekDetailScreenUIEvent.ClearErrorToast)
        }
    }

    LaunchedEffect(Unit) {
        println("$TAG, calling initial data setup onetime")
        viewModel.setInitialData(trekId)
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
    
    // "Add a place". The phone's own camera app and Android's photo picker do the
    // picking, so the app needs no camera or storage permission.
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) addPoiViewModel.onPhotoChosen(uri.toString())
    }
    // Remembered across process death: low-memory phones kill the app while the
    // camera is up, and the result arrives with nothing else to say where it went.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = cameraUri
        if (saved && uri != null) addPoiViewModel.onPhotoChosen(uri.toString())
    }

    LaunchedEffect(addPoiState.message) {
        addPoiState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            addPoiViewModel.clearMessage()
        }
    }

    val addPoiActions = AddPoiActions(
        onAddClick = {
            state.trek?.let { trek ->
                addPoiViewModel.open(
                    trekId = trekId,
                    boundingBox = trek.boundingBox,
                    knownLocation = state.liveLocation,
                    canUseLocation = PermissionUtil.hasLocationPermission(context)
                )
            }
        },
        onDismiss = addPoiViewModel::dismiss,
        onTakePhoto = {
            try {
                val uri = CameraFiles.newPhotoUri(context)
                cameraUri = uri
                takePhotoLauncher.launch(uri)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No camera app found on this phone", Toast.LENGTH_SHORT).show()
            }
        },
        onPickPhoto = {
            pickPhotoLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onNoteChange = addPoiViewModel::onNoteChanged,
        onEditLocation = addPoiViewModel::startPicking,
        onUseCurrentLocation = {
            addPoiViewModel.useCurrentLocation(PermissionUtil.hasLocationPermission(context))
        },
        onTermsChecked = addPoiViewModel::onTermsChecked,
        onOpenTerms = { openDetailsLink(context, CustomPoiRules.CONTENT_TERMS_URL) },
        onSave = addPoiViewModel::save,
        onCancelPicking = addPoiViewModel::cancelPicking,
        onLocationPicked = addPoiViewModel::onLocationPicked
    )

    TrekDetailScreen(
        state = state,
        onBackClick = onBackClick,
        onStartHike = {
            if(PermissionUtil.hasLocationPermission(context)) {
                viewModel.onEvent(TrekDetailScreenUIEvent.OnStartHike(trekId))
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
        onStopHike = { viewModel.onEvent(TrekDetailScreenUIEvent.OnStopHike) },
        onAreaPromptGone = { viewModel.onEvent(TrekDetailScreenUIEvent.ClearAreaPrompt) },
        onMapTap = { point, toleranceM ->
            viewModel.onEvent(
                TrekDetailScreenUIEvent.OnMapTap(point.longitude(), point.latitude(), toleranceM)
            )
        },
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
        snackbarHostState = snackBarHostState,
        addPoiState = addPoiState,
        addPoiActions = addPoiActions
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
    onAreaPromptGone: () -> Unit = {},
    onMapTap: (point: Point, toleranceM: Double) -> Unit = { _, _ -> },
    onDownloadHikeClick: () -> Unit,
    onPlanClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    addPoiState: AddPoiUiState = AddPoiUiState(),
    addPoiActions: AddPoiActions = AddPoiActions()
) {
    val context = LocalContext.current
    var selectedPoi by remember { mutableStateOf<TrekPoi?>(null) }

    // The route showing: the default, or whatever the user's taps made of it.
    // The slider, the climb, the distance and the highest point all follow it.
    val routeView = state.routeView
    val elevationPoints = routeView?.profile.orEmpty()

    // Total ascent of the route showing. This is the number that separates 25
    // flat kilometres from 25 hard ones, which max elevation on its own cannot say.
    val totalClimbM = routeView?.climbM?.takeIf { elevationPoints.size >= 2 }
    val highestM = remember(elevationPoints) { elevationPoints.maxOfOrNull { it.elevationM } }
    var elevationIndex by remember(elevationPoints) { mutableIntStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }

    // The slider has to stop where the detail sheet starts, and that sheet is as
    // tall as its contents - so measure it rather than guessing a height that
    // would drift the moment a line of text wraps.
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    // Kept across rotation. Collapsed shows only the trek name and the chevron.
    var isSheetExpanded by rememberSaveable { mutableStateOf(true) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isSheetExpanded) 0f else 180f,
        animationSpec = tween(SHEET_ANIM_MS),
        label = "sheet chevron"
    )
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

    // Placing a pin starts from where the place already is, zoomed in far enough
    // to be precise. With no location yet the camera stays on the whole trek and
    // the user finds the spot themselves.
    LaunchedEffect(addPoiState.isPicking) {
        if (!addPoiState.isPicking) return@LaunchedEffect
        addPoiState.location?.let { start ->
            mapViewportState.setCameraOptions(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(start.lon, start.lat))
                    .zoom(15.0)
                    .build()
            )
        }
    }

    Box (
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {

        if (routeView != null && state.trek != null) {
            val geoJsonString = remember(routeView.line) { lineGeoJson(routeView.line) }
            val otherPathsGeoJson = remember(routeView.others) { sectionsGeoJson(routeView.others) }
            val boundingBox = state.trek?.boundingBox

            geoJsonString.let {
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
                    modifier = Modifier.fillMaxSize().padding(bottom = MAP_BOTTOM_PADDING),
                    // The other paths, lighter. A tap near one adds it to the
                    // route or swaps it in.
                    otherPathsGeoJson = otherPathsGeoJson,
                    // A card anchored to a marker has to go when the map is
                    // tapped elsewhere, and when the marker slides out from
                    // under it on a pan.
                    onMapTap = { point, toleranceM ->
                        selectedPoi = null
                        if (!isScrubbing && !addPoiState.isPicking) onMapTap(point, toleranceM)
                    },
                    onMapPan = { selectedPoi = null }
                ) {
                    // Scrubbing and browsing are mutually exclusive: leaving the
                    // pins up would bury the trekker among them, and the whole
                    // point of the gesture is to follow one marker.
                    if (!isScrubbing) {
                        PoiMarkers(
                            pois = state.pois,
                            offRouteIds = state.offRoutePoiIds,
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
                    // While a pin is being placed, back means "never mind the pin".
                    .clickable {
                        if (addPoiState.isPicking) addPoiActions.onCancelPicking() else onBackClick()
                    },
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
        // Everything that is not the map or the pin gets out of the way while a
        // pin is being placed: the slider, the POI card, the banner and the sheet.
        if (elevationPoints.isNotEmpty() && !addPoiState.isPicking) {
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
        selectedPoi?.takeIf { !addPoiState.isPicking }?.let { poi ->
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

        // Shown once whenever the view model asks: on entry outside the area and
        // on each Start Hike pressed outside it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 80.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            AreaPrompt(
                show = state.showAreaPrompt,
                onGone = onAreaPromptGone
            )
        }

        // A download from before routes, opened with no signal: nothing to draw
        // until it has been updated once.
        if (state.needsConnection) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    text = "Connect to the internet once to update this trek",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        if (!addPoiState.isPicking) Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { sheetHeightPx = it.height }
                .clip(RoundedCornerShape(topEnd = 14.dp, topStart = 14.dp))
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Name and the collapse chevron are all a collapsed sheet shows.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = state.trek?.name.orEmpty(),
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { isSheetExpanded = !isSheetExpanded }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isSheetExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurface,
                            // Down when open (fold away), up when folded (bring back).
                            modifier = Modifier.rotate(chevronRotation)
                        )
                    }
                }
                // The slider and the Add-a-place button sit on this sheet's measured
                // height, so they follow the animation frame by frame.
                AnimatedVisibility(
                    visible = isSheetExpanded,
                    enter = expandVertically(tween(SHEET_ANIM_MS)) + fadeIn(tween(SHEET_ANIM_MS)),
                    exit = shrinkVertically(tween(SHEET_ANIM_MS)) + fadeOut(tween(SHEET_ANIM_MS))
                ) {
                    Column {
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
                            routeView?.km?.let { add("%.1f km".format(it)) }
                            totalClimbM?.let { add("↑ %,d m".format(it)) }
                            // "4,283 m" alone reads as though it might be the climb.
                            highestM?.let { add("Max %,d m".format(it)) }
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
                                // Switched remotely (app config `features.planner`). Off,
                                // Download takes the whole row.
                                if (state.isPlannerEnabled) {
                                    SecondaryButton(
                                        text = "Plan",
                                        onClick = { onPlanClick() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                SecondaryButton(
                                    text = "Download",
                                    onClick = { onDownloadHikeClick() },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.fillMaxWidth().height(20.dp))
            }
        }
        
        // "Add a place" sits just above the sheet, on the side the elevation
        // slider is not. Waits for the trek: without its bounds there is no
        // trekking area to check a location against.
        if (!addPoiState.isPicking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, bottom = sheetHeight + 12.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                AddPlaceButton(
                    onClick = addPoiActions.onAddClick,
                    enabled = state.trek != null
                )
            }
        }

        if (addPoiState.isPicking) {
            PoiLocationPicker(
                mapViewportState = mapViewportState,
                boundingBox = state.trek?.boundingBox.orEmpty(),
                mapBottomPadding = MAP_BOTTOM_PADDING,
                onConfirm = addPoiActions.onLocationPicked,
                onCancel = addPoiActions.onCancelPicking
            )
        }

        // Hidden, not dismissed, while the pin is placed: the form is in the
        // ViewModel and comes back as it was.
        if (addPoiState.isDialogVisible) {
            AddPoiDialog(
                state = addPoiState,
                actions = addPoiActions
            )
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
    offRouteIds: Set<String> = emptySet(),
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
                // Things beyond the trailhead, or beside a path the route does not
                // take, are context, not part of the walk.
                iconOpacity = if (poi.isOnRoute && poi.id !in offRouteIds) 1.0 else 0.55
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

private const val SHEET_ANIM_MS = 220
