package com.via.himalaya.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.via.himalaya.data.models.PlannedDay
import com.via.himalaya.data.models.PoiCategory
import com.via.himalaya.data.models.TrekElevationPoint
import com.via.himalaya.data.models.TrekPlan
import com.via.himalaya.data.models.TrekPoi
import com.via.himalaya.domain.model.toGeoJsonString
import com.via.himalaya.presentation.trekDetail.TrekDetailScreenUIState
import com.via.himalaya.presentation.trekDetail.TrekDetailViewModel
import com.via.himalaya.presentation.trekPlan.TrekPlanScreenUIState
import com.via.himalaya.presentation.trekPlan.TrekPlanViewModel
import com.via.himalaya.ui.components.CampWheel
import com.via.himalaya.ui.components.PrimaryButton
import com.via.himalaya.ui.components.TrekMap
import kotlin.math.abs
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.mapbox.maps.EdgeInsets

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
 * The sheet grows to whatever is left above this rather than to a fixed height:
 * what matters is seeing the camp under discussion, its ring and enough trail
 * either side to judge the gap.
 */
private val MIN_MAP_HEIGHT = 300.dp

/**
 * Long enough to read the move as travel between two places, short enough not to
 * stall a user scrolling through camps.
 */
private const val CAMERA_EASE_MS = 650L

private val PREVIEW_COLOR = Color(0xFFFF9800)

/**
 * Ring around the camp under discussion. Kept just clear of the 28dp marker
 * icon - wide enough to read as "this one", tight enough not to swallow the
 * neighbouring pins it is meant to distinguish it from.
 */
private const val PREVIEW_RING_RADIUS = 15.0
private const val PREVIEW_RING_STROKE = 3.0

/** Where the POI card sits below the back button. */
private val CARD_TOP_INSET = 80.dp

/** Breathing room between the bottom of the card and the ringed camp. */
private val CARD_CLEARANCE = 12.dp

/** Elevation samples are one per 100 m, which is what makes index a ruler. */
private const val SAMPLES_PER_KM = 10

/** How close a stored camp has to be to a POI to count as the same place. */
private const val REANCHOR_TOLERANCE_DEG = 0.0015

private enum class PlanMode { LIST, VIEW, BUILD }

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

/**
 * Finds the POI a saved day refers to.
 *
 * By id first, then by position. OSM ids are not permanent - a node deleted and
 * re-added gets a new one - so a plan saved last season can point at an id that
 * no longer exists. The stored coordinates are what let it survive that, which is
 * why they are kept alongside the id rather than instead of it.
 */
private fun resolveCamp(day: PlannedDay, camps: List<TrekPoi>): TrekPoi? {
    camps.firstOrNull { it.id != null && it.id == day.poiId }?.let { return it }
    return camps
        .filter {
            abs(it.lat - day.lat) < REANCHOR_TOLERANCE_DEG &&
                abs(it.lon - day.lon) < REANCHOR_TOLERANCE_DEG
        }
        .minByOrNull { abs(it.lat - day.lat) + abs(it.lon - day.lon) }
}

@Composable
fun TrekPlanScreenRoot(
    viewModel: TrekDetailViewModel,
    planViewModel: TrekPlanViewModel,
    trekId: String,
    coordinateUrl: String,
    onBackClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val planState by planViewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.setInitialData(coordinateUrl, trekId)
        planViewModel.load(trekId)
    }

    LaunchedEffect(planState.errorToast) {
        planState.errorToast?.let { message ->
            snackbarHostState.showSnackbar(message = message, withDismissAction = true)
            planViewModel.clearErrorToast()
        }
    }

    TrekPlanScreen(
        state = state,
        planState = planState,
        snackbarHostState = snackbarHostState,
        onSave = planViewModel::savePlan,
        onDelete = planViewModel::deletePlan,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekPlanScreen(
    state: TrekDetailScreenUIState,
    planState: TrekPlanScreenUIState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onSave: (List<TrekPoi>) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val mapViewportState = rememberMapViewportState()

    // Off-route entries sit beside a trailhead rather than on the walk, so they
    // cannot end a day. Sorting by distance puts them in walking order.
    val camps = remember(state.pois) {
        state.pois
            .filter { it.category in CAMPABLE && it.offRoute == null && it.offsetM <= MAX_OFFSET_M }
            .sortedBy { it.distAlongKm }
    }
    val profile = state.trek?.elevationProfile.orEmpty()

    // A stack: days are appended, and only the last one comes off. That removes
    // every re-validation question an editable middle would raise.
    val chosen = remember { mutableStateListOf<TrekPoi>() }
    var previewCamp by remember { mutableStateOf<TrekPoi?>(null) }

    var mode by remember { mutableStateOf(PlanMode.LIST) }
    var viewingPlanId by remember { mutableStateOf<Long?>(null) }

    // Nothing saved means nothing to list, so go straight to building. Waits for
    // the first read: deciding on an empty list would flash the builder at
    // someone who already has plans.
    LaunchedEffect(planState.isLoading, planState.savedPlans.size) {
        if (planState.isLoading) return@LaunchedEffect
        if (planState.savedPlans.isEmpty() && mode == PlanMode.LIST) mode = PlanMode.BUILD
    }

    // A save lands the user on the plan they just made.
    LaunchedEffect(planState.lastSavedPlanId) {
        planState.lastSavedPlanId?.let {
            viewingPlanId = it
            mode = PlanMode.VIEW
            chosen.clear()
            previewCamp = null
        }
    }

    val viewingPlan = planState.savedPlans.firstOrNull { it.planId == viewingPlanId }

    val lastChosenKm = chosen.lastOrNull()?.distAlongKm ?: -1.0
    val planFull = chosen.size >= MAX_NIGHTS
    fun selectable(camp: TrekPoi) = !planFull && camp.distAlongKm > lastChosenKm

    // Camps to ring on the map: the one being previewed while building, or every
    // camp in the plan being read.
    val highlighted: List<TrekPoi> = when (mode) {
        PlanMode.BUILD -> listOfNotNull(previewCamp)
        PlanMode.VIEW -> viewingPlan?.days?.mapNotNull { resolveCamp(it, camps) }.orEmpty()
        PlanMode.LIST -> emptyList()
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.Expanded,
            skipHiddenState = true
        )
    )

    val density = LocalDensity.current
    val peekPx = with(density) { SHEET_PEEK_HEIGHT.toPx() }
    var containerHeightPx by remember { mutableIntStateOf(0) }
    var cardHeightPx by remember { mutableIntStateOf(0) }

    // How much map the POI card covers at the top, once it is on screen. Zero
    // when there is no card, so the camera reclaims the space immediately.
    val cardCoverPx: Float = if (previewCamp != null && cardHeightPx > 0) {
        with(density) { (CARD_TOP_INSET + CARD_CLEARANCE).toPx() } + cardHeightPx
    } else {
        0f
    }

    /**
     * How much map the sheet is covering, in pixels.
     *
     * requireOffset() is the sheet's top edge measured from the top of the
     * scaffold, so everything below it is hidden. It throws before the sheet has
     * been laid out, hence runCatching - until then fall back to the peek height,
     * which is the least it can ever cover.
     *
     * Read at the moment the camera moves rather than held in state: it changes
     * continuously while the sheet is dragged, and recomposing the whole screen on
     * every frame of that drag would be wasteful for a value only the camera uses.
     */
    fun sheetCoverPx(): Double {
        val sheetTop = runCatching { scaffoldState.bottomSheetState.requireOffset() }.getOrNull()
        return if (sheetTop != null && containerHeightPx > 0) {
            (containerHeightPx - sheetTop)
                .coerceIn(peekPx, containerHeightPx.toFloat())
                .toDouble()
        } else {
            peekPx.toDouble()
        }
    }

    // Eased rather than snapped, so the user watches the map travel from the last
    // camp to this one and gets a feel for the gap between them.
    //
    // Camera padding, not a map margin: padding tells Mapbox to centre within the
    // *unpadded* region, so the camp lands in the band of map that nothing is
    // covering - below the POI card, above the sheet. Both insets are measured,
    // because both change: the sheet grows with each day added, and the card grows
    // with however many tags the camp carries.
    //
    // cardHeightPx is a key, so the first preview settles into place once the card
    // has measured itself. Card heights repeat between camps, so that correction
    // is usually a no-op rather than a second animation.
    LaunchedEffect(previewCamp, chosen.size, cardHeightPx) {
        previewCamp?.let { camp ->
            mapViewportState.easeTo(
                CameraOptions.Builder()
                    .center(Point.fromLngLat(camp.lon, camp.lat))
                    .padding(
                        EdgeInsets(cardCoverPx.toDouble(), 0.0, sheetCoverPx(), 0.0)
                    )
                    .build(),
                MapAnimationOptions.mapAnimationOptions { duration(CAMERA_EASE_MS) }
            )
        }
    }

    BottomSheetScaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK_HEIGHT,
        sheetContainerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        sheetContent = {
            PlanSheet(
                mode = mode,
                planState = planState,
                viewingPlan = viewingPlan,
                camps = camps,
                profile = profile,
                chosen = chosen,
                previewCamp = previewCamp,
                planFull = planFull,
                isSelectable = ::selectable,
                onPreviewChange = { previewCamp = it },
                onAddDay = {
                    previewCamp?.let { chosen.add(it) }
                    previewCamp = null
                },
                onRemoveLastDay = { chosen.removeAt(chosen.lastIndex) },
                onSave = { onSave(chosen.toList()) },
                onOpenPlan = {
                    viewingPlanId = it
                    mode = PlanMode.VIEW
                },
                onNewPlan = {
                    chosen.clear()
                    previewCamp = null
                    mode = PlanMode.BUILD
                },
                onBackToList = {
                    previewCamp = null
                    mode = PlanMode.LIST
                },
                onDeletePlan = {
                    onDelete(it)
                    mode = PlanMode.LIST
                }
            )
        }
    ) {
        // Measured here rather than from the screen: this is the scaffold's body,
        // which is the same coordinate space the sheet's offset is reported in.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerHeightPx = it.height }
        ) {
            val geoJson = state.geoData?.geometry?.toGeoJsonString()

            if (geoJson != null) {
                TrekMap(
                    geoJson = geoJson,
                    boundingBox = state.trek?.boundingBox,
                    mapViewportState = mapViewportState,
                    modifier = Modifier.fillMaxSize().padding(bottom = SHEET_PEEK_HEIGHT)
                ) {
                    val campIcon = rememberIconImage(
                        key = "plan-camp",
                        painter = painterResource(R.drawable.ic_poi_camp_site)
                    )

                    // Rings first, so pins sit inside them rather than under them.
                    highlighted.forEach { camp ->
                        CircleAnnotation(point = Point.fromLngLat(camp.lon, camp.lat)) {
                            circleRadius = PREVIEW_RING_RADIUS
                            circleColor = Color.Transparent
                            circleStrokeWidth = PREVIEW_RING_STROKE
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

            if (mode == PlanMode.BUILD) {
                previewCamp?.let { camp ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = CARD_TOP_INSET, start = 16.dp, end = 16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        PoiDetailCard(
                            poi = camp,
                            onDismiss = { previewCamp = null },
                            // Measured because the card grows with however many
                            // tags a camp carries - a reserved height would be
                            // wrong for most of them.
                            modifier = Modifier.onSizeChanged { cardHeightPx = it.height }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanSheet(
    mode: PlanMode,
    planState: TrekPlanScreenUIState,
    viewingPlan: TrekPlan?,
    camps: List<TrekPoi>,
    profile: List<TrekElevationPoint>,
    chosen: List<TrekPoi>,
    previewCamp: TrekPoi?,
    planFull: Boolean,
    isSelectable: (TrekPoi) -> Boolean,
    onPreviewChange: (TrekPoi?) -> Unit,
    onAddDay: () -> Unit,
    onRemoveLastDay: () -> Unit,
    onSave: () -> Unit,
    onOpenPlan: (Long) -> Unit,
    onNewPlan: () -> Unit,
    onBackToList: () -> Unit,
    onDeletePlan: (Long) -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxScrollHeight = (screenHeight - MIN_MAP_HEIGHT).coerceAtLeast(240.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (mode) {
                    PlanMode.LIST -> "Saved plans"
                    PlanMode.VIEW -> "Day ${viewingPlan?.dayCount ?: 0} plan"
                    PlanMode.BUILD -> "Plan"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (mode == PlanMode.BUILD) {
                Text(
                    text = "${chosen.size + 1} days",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(modifier = Modifier.weight(1f))
            if (mode != PlanMode.LIST && planState.savedPlans.isNotEmpty()) {
                Text(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onBackToList)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    text = "All plans",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxScrollHeight)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            when {
                planState.isLoading -> Unit

                camps.isEmpty() -> Text(
                    modifier = Modifier.padding(vertical = 16.dp),
                    text = "No campsites mapped on this trek yet.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                mode == PlanMode.LIST -> SavedPlansList(
                    plans = planState.savedPlans,
                    camps = camps,
                    onOpenPlan = onOpenPlan,
                    onNewPlan = onNewPlan
                )

                mode == PlanMode.VIEW && viewingPlan != null -> SavedPlanDetail(
                    plan = viewingPlan,
                    camps = camps,
                    profile = profile,
                    onDelete = { onDeletePlan(viewingPlan.planId) }
                )

                else -> PlanBuilder(
                    camps = camps,
                    profile = profile,
                    chosen = chosen,
                    previewCamp = previewCamp,
                    planFull = planFull,
                    isSaving = planState.isSaving,
                    isSelectable = isSelectable,
                    onPreviewChange = onPreviewChange,
                    onAddDay = onAddDay,
                    onRemoveLastDay = onRemoveLastDay,
                    onSave = onSave
                )
            }
        }
    }
}

@Composable
private fun SavedPlansList(
    plans: List<TrekPlan>,
    camps: List<TrekPoi>,
    onOpenPlan: (Long) -> Unit,
    onNewPlan: () -> Unit
) {
    plans.forEach { plan ->
        // Named by its stops rather than by a title nobody was asked for. Two
        // plans for the same trek differ by where they stop, which is exactly
        // what this shows.
        val stops = plan.days.joinToString(", ") { day ->
            day.campName ?: resolveCamp(day, camps)?.name ?: "Unnamed camp"
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenPlan(plan.planId) }
                // surfaceVariant, not onPrimary. onPrimary is a *content* colour
                // for drawing on top of primary, and it is pure white in both
                // themes - so using it as a surface gave a white card in dark
                // mode, under near-white onSurface text. surfaceVariant is the
                // role that is actually defined to carry onSurface text, and it
                // adapts per theme.
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "${plan.dayCount} days",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                modifier = Modifier.padding(top = 2.dp),
                text = stops,
                fontSize = 12.sp,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    PrimaryButton(
        text = "+ New plan",
        onClick = onNewPlan,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
    )
}

@Composable
private fun SavedPlanDetail(
    plan: TrekPlan,
    camps: List<TrekPoi>,
    profile: List<TrekElevationPoint>,
    onDelete: () -> Unit
) {
    var previousKm = 0.0
    plan.days.forEachIndexed { index, day ->
        val camp = resolveCamp(day, camps)
        val km = camp?.distAlongKm
        DayRow(
            day = index + 1,
            endName = day.campName ?: camp?.name ?: "Unnamed camp",
            endElevationM = km?.let { elevationAtKm(profile, it) } ?: camp?.eleM?.toInt(),
            legKm = km?.let { it - previousKm },
            climbM = km?.let { climbBetweenKm(profile, previousKm, it) },
            onDelete = null
        )
        if (km != null) previousKm = km
    }

    FinalDayRow(day = plan.dayCount)

    // Removes the plan only. Anything downloaded for this trek stays in
    // Downloads, which is the separation the user asked for.
    PrimaryButton(
        text = "Delete plan",
        onClick = onDelete,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
    )
}

@Composable
private fun PlanBuilder(
    camps: List<TrekPoi>,
    profile: List<TrekElevationPoint>,
    chosen: List<TrekPoi>,
    previewCamp: TrekPoi?,
    planFull: Boolean,
    isSaving: Boolean,
    isSelectable: (TrekPoi) -> Boolean,
    onPreviewChange: (TrekPoi?) -> Unit,
    onAddDay: () -> Unit,
    onRemoveLastDay: () -> Unit,
    onSave: () -> Unit
) {
    chosen.forEachIndexed { index, camp ->
        val previousKm = if (index == 0) 0.0 else chosen[index - 1].distAlongKm
        DayRow(
            day = index + 1,
            endName = camp.name ?: "Unnamed ${PoiCategory.label(camp.category).lowercase()}",
            // The profile is the more reliable source: OSM rarely tags camps
            // with an elevation, so poi.eleM is usually null.
            endElevationM = elevationAtKm(profile, camp.distAlongKm) ?: camp.eleM?.toInt(),
            legKm = camp.distAlongKm - previousKm,
            climbM = climbBetweenKm(profile, previousKm, camp.distAlongKm),
            // Only the last day can come off, which is what keeps this a stack.
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

    PrimaryButton(
        text = if (isSaving) "Saving..." else "Save",
        enabled = chosen.isNotEmpty() && !isSaving,
        onClick = onSave,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp)
    )
}

@Composable
private fun DayRow(
    day: Int,
    endName: String,
    endElevationM: Int?,
    legKm: Double?,
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
            text = when {
                legKm != null && climbM != null -> "%.1f km  ↑ %,d m".format(legKm, climbM)
                legKm != null -> "%.1f km".format(legKm)
                // A camp whose POI has gone from the bundle: the plan still
                // shows what was chosen, just not how far it is.
                else -> "—"
            },
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
