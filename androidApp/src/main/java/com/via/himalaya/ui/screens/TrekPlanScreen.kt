package com.via.himalaya.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.CircleAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.extension.compose.annotation.rememberIconImage
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.via.himalaya.R
import com.via.himalaya.data.models.PoiCategory
import com.via.himalaya.data.models.TrekElevationPoint
import com.via.himalaya.data.models.TrekPoi
import com.via.himalaya.domain.model.toGeoJsonString
import com.via.himalaya.presentation.trekDetail.TrekDetailScreenUIState
import com.via.himalaya.presentation.trekDetail.TrekDetailViewModel
import com.via.himalaya.ui.components.CampWheel
import com.via.himalaya.ui.components.PrimaryButton
import com.via.himalaya.ui.components.TrekMap

/**
 * Categories that can end a day. Everything else is noise while planning - a
 * ford matters when you are walking, not when you are deciding where to sleep.
 */
private val CAMPABLE = setOf(PoiCategory.CAMP_SITE, PoiCategory.STAY, PoiCategory.SHELTER)

/** Beyond this a "nearby" camp is really a separate expedition. */
private const val MAX_OFFSET_M = 1000

/**
 * Five chosen camps means six days, counting the last leg to the trail end.
 * Beyond that this stops being a trek and starts being an expedition.
 */
private const val MAX_NIGHTS = 5

/** Enough for the drag handle and the title row, and nothing else. */
private val SHEET_PEEK_HEIGHT = 92.dp

/**
 * How much map the expanded sheet must leave behind.
 *
 * The sheet grows to whatever is left above this, rather than to a fixed height:
 * the thing that actually matters is being able to see the previewed camp, its
 * orange ring and enough trail either side to judge the gap. Below roughly this
 * much, the map stops answering that question - and on a taller phone there is
 * no reason to cap the plan any shorter than that.
 */
private val MIN_MAP_HEIGHT = 300.dp

/**
 * Long enough to read the move as travel between two places, short enough not to
 * stall a user scrolling through camps. Instant jumps lose the sense of how far
 * apart the options are, which is half of what the wheel is for.
 */
private const val CAMERA_EASE_MS = 650L

private val PREVIEW_COLOR = Color(0xFFFF9800)

/** Elevation samples are one per 100 m, which is what makes index a ruler. */
private const val SAMPLES_PER_KM = 10

/** Ground height at a point along the trail, or null outside the profile. */
private fun elevationAtKm(profile: List<TrekElevationPoint>, km: Double): Int? =
    profile.getOrNull((km * SAMPLES_PER_KM).toInt())?.elevationM

/**
 * Cumulative ascent between two points on the trail - every uphill step added
 * up, descents ignored.
 *
 * Not `highest point - starting height`: that only measures how far above the
 * start you get, so a day that climbs a ridge, drops into a gully and climbs
 * again is counted once instead of twice. On this terrain that is most days.
 *
 * Slight overcount is possible, since noise in a 30 m grid adds a metre here and
 * there across hundreds of samples. Sampling at 100 m already smooths most of
 * it, and the alternative - ignoring steps below a threshold - starts throwing
 * away real climb on gentle ground.
 */
private fun climbBetweenKm(
    profile: List<TrekElevationPoint>,
    fromKm: Double,
    toKm: Double
): Int? {
    if (profile.size < 2) return null
    val from = (fromKm * SAMPLES_PER_KM).toInt().coerceIn(profile.indices)
    val to = (toKm * SAMPLES_PER_KM).toInt().coerceIn(profile.indices)
    if (to <= from) return null
    return (from until to).sumOf { i ->
        (profile[i + 1].elevationM - profile[i].elevationM).coerceAtLeast(0)
    }
}

@Composable
fun TrekPlanScreenRoot(
    viewModel: TrekDetailViewModel,
    trekId: String,
    coordinateUrl: String,
    onBackClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.setInitialData(coordinateUrl, trekId)
    }

    TrekPlanScreen(state = state, onBackClick = onBackClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekPlanScreen(
    state: TrekDetailScreenUIState,
    onBackClick: () -> Unit = {}
) {
    val mapViewportState = rememberMapViewportState()

    // Off-route entries sit beside a trailhead rather than on the walk, so they
    // cannot end a day. Sorting by distance puts them in walking order, which is
    // the order everything on this screen wants.
    val camps = remember(state.pois) {
        state.pois
            .filter { it.category in CAMPABLE && it.offRoute == null && it.offsetM <= MAX_OFFSET_M }
            .sortedBy { it.distAlongKm }
    }

    val elevationProfile = state.trek?.elevationProfile.orEmpty()

    // A stack: days are appended, and only the last one comes off. That removes
    // every re-validation question an editable middle would raise - if day 2
    // could change under days 3 and 4, each of those would need rechecking
    // against a boundary that just moved.
    val chosen = remember { mutableStateListOf<TrekPoi>() }

    // What the wheel is resting on. A preview, not a choice - it lights up on the
    // map and opens its card, and is only committed by the add button.
    var previewCamp by remember { mutableStateOf<TrekPoi?>(null) }

    val lastChosenKm = chosen.lastOrNull()?.distAlongKm ?: -1.0
    val planFull = chosen.size >= MAX_NIGHTS
    fun selectable(camp: TrekPoi) = !planFull && camp.distAlongKm > lastChosenKm

    // Eased rather than snapped, so the user watches the map travel from the last
    // camp to this one and gets a feel for the gap between them.
    LaunchedEffect(previewCamp) {
        previewCamp?.let { camp ->
            mapViewportState.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(camp.lon, camp.lat))
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(CAMERA_EASE_MS) }
            )
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            // Open showing the plan. Landing on a collapsed sheet would hide the
            // one control the screen exists for.
            initialValue = SheetValue.Expanded,
            skipHiddenState = true
        )
    )

    BottomSheetScaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK_HEIGHT,
        sheetContainerColor = MaterialTheme.colorScheme.background,
        sheetContent = {
            PlanSheet(
                camps = camps,
                chosen = chosen,
                profile = elevationProfile,
                previewCamp = previewCamp,
                planFull = planFull,
                isSelectable = ::selectable,
                onPreviewChange = { previewCamp = it },
                onAddDay = {
                    previewCamp?.let { chosen.add(it) }
                    // Finalising clears the preview: the highlight and the card
                    // belong to a decision that is still being made.
                    previewCamp = null
                },
                onRemoveLastDay = { chosen.removeAt(chosen.lastIndex) }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val geoJson = state.geoData?.geometry?.toGeoJsonString()

            if (geoJson != null) {
                TrekMap(
                    geoJson = geoJson,
                    boundingBox = state.trek?.boundingBox,
                    mapViewportState = mapViewportState,
                    // Keeps the trail clear of the collapsed sheet, which
                    // overlays rather than displaces the map.
                    modifier = Modifier.fillMaxSize().padding(bottom = SHEET_PEEK_HEIGHT)
                ) {
                    val campIcon = rememberIconImage(
                        key = "plan-camp",
                        painter = painterResource(R.drawable.ic_poi_camp_site)
                    )

                    // Ring first, so the pin sits inside it rather than under it.
                    previewCamp?.let { camp ->
                        CircleAnnotation(point = Point.fromLngLat(camp.lon, camp.lat)) {
                            circleRadius = 22.0
                            circleColor = Color.Transparent
                            circleStrokeWidth = 4.0
                            circleStrokeColor = PREVIEW_COLOR
                        }
                    }

                    camps.forEach { camp ->
                        PointAnnotation(point = Point.fromLngLat(camp.lon, camp.lat)) {
                            iconImage = campIcon
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
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
                        tint = Color.Black
                    )
                }
            }

            previewCamp?.let { camp ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 80.dp, start = 16.dp, end = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    PoiDetailCard(poi = camp, onDismiss = { previewCamp = null })
                }
            }
        }
    }

}

/**
 * The title row is what the collapsed peek shows; everything under it scrolls,
 * because six days of rows plus a wheel will not fit a small screen. Save sits at
 * the end of that scroll - it is the last thing you do, so it reads better after
 * the plan than above it.
 */
@Composable
private fun PlanSheet(
    camps: List<TrekPoi>,
    chosen: List<TrekPoi>,
    profile: List<TrekElevationPoint>,
    previewCamp: TrekPoi?,
    planFull: Boolean,
    isSelectable: (TrekPoi) -> Boolean,
    onPreviewChange: (TrekPoi?) -> Unit,
    onAddDay: () -> Unit,
    onRemoveLastDay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Keeps the last thing in the sheet clear of the system nav bar,
            // measured rather than assumed.
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Plan",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${chosen.size + 1} days",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(modifier = Modifier.weight(1f))
        }

        // Derived from this device's screen rather than fixed, so a tall phone
        // shows more of the plan and a small one still keeps a usable map.
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val maxScrollHeight = (screenHeight - MIN_MAP_HEIGHT).coerceAtLeast(240.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxScrollHeight)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            if (camps.isEmpty()) {
                Text(
                    modifier = Modifier.padding(vertical = 16.dp),
                    text = "No campsites mapped on this trek yet.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            chosen.forEachIndexed { index, camp ->
                val previousKm = if (index == 0) 0.0 else chosen[index - 1].distAlongKm
                DayRow(
                    day = index + 1,
                    endName = camp.name
                        ?: "Unnamed ${PoiCategory.label(camp.category).lowercase()}",
                    // The profile is the more reliable source: OSM rarely tags
                    // camps with an elevation, so poi.eleM is usually null.
                    endElevationM = elevationAtKm(profile, camp.distAlongKm)
                        ?: camp.eleM?.toInt(),
                    legKm = camp.distAlongKm - previousKm,
                    climbM = climbBetweenKm(profile, previousKm, camp.distAlongKm),
                    // Only the last day can come off, which is what keeps this a
                    // stack.
                    onDelete = if (index == chosen.lastIndex) onRemoveLastDay else null
                )
            }

            if (planFull) {
                FinalDayRow(day = chosen.size + 1)
            } else {
                Text(
                    modifier = Modifier.padding(top = 10.dp),
                    text = "Day ${chosen.size + 1} ends at",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                CampWheel(
                    camps = camps,
                    isSelectable = isSelectable,
                    onFocusedChange = onPreviewChange,
                    modifier = Modifier.padding(top = 4.dp)
                )

                val canAdd = previewCamp?.let(isSelectable) == true
                Text(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canAdd, onClick = onAddDay)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    text = "+ Day ${chosen.size + 2}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (canAdd) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            // Last thing in the sheet, after the plan it commits. Scrolls with
            // the content rather than floating - there is nothing to save until
            // at least one day has been chosen anyway.
            PrimaryButton(
                text = "Save",
                enabled = chosen.isNotEmpty(),
                onClick = { /* Room persistence lands next */ },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
            )
        }
    }
}

@Composable
private fun DayRow(
    day: Int,
    endName: String,
    endElevationM: Int?,
    legKm: Double,
    climbM: Int?,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Day $day",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(52.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = endName,
                fontSize = 13.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Sleeping height, under the name rather than beside it - camp names
            // run long, and this must not be what gets truncated.
            endElevationM?.let {
                Text(
                    text = "%,d m".format(it),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = if (climbM != null) "%.1f km  ↑ %,d m".format(legKm, climbM)
            else "%.1f km".format(legKm),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(modifier = Modifier.width(34.dp))
        }
    }
}

@Composable
private fun FinalDayRow(day: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Day $day",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(
            modifier = Modifier.weight(1f),
            text = "to the end of the trail",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
