package com.via.himalaya.presentation.trekDetail

import com.via.himalaya.data.models.Loc
import com.via.himalaya.data.models.TrekDetail
import com.via.himalaya.data.models.TrekPoi
import com.via.himalaya.domain.model.Feature
import com.via.himalaya.domain.model.LocationResponse
import com.via.himalaya.domain.model.RouteNetwork

data class TrekDetailScreenUIState(
    val trek: TrekDetail? = null,
    val isLoading: Boolean = false,
    val errorToast: String? = null,
    val messageDisplay: String? = null,
    /** The trek's sections, from its routes file. Null until it loads. */
    val network: RouteNetwork? = null,
    /** The route showing: the default at first, then whatever taps made of it. */
    val routeView: RouteView? = null,
    /**
     * Sorted by distance along the route showing, which is what their
     * dist_along_km now holds. Empty until the bundle loads, or if the trek has none.
     */
    val pois: List<TrekPoi> = emptyList(),
    /** POIs beside a section the route does not use: faded on the map. */
    val offRoutePoiIds: Set<String> = emptySet(),
    /**
     * The trek has no routes file to show - a download saved before routes
     * existed, opened offline. The screen asks for a connection instead of an
     * empty map; the download is redone the next time there is one.
     */
    val needsConnection: Boolean = false,
    val initialLocation: LocationResponse? = null,
    val liveLocation: Loc? = null,
    val isNearTrekStart: Boolean = false,
    val isTrekking: Boolean = false,
    /**
     * Show the "not in the trekking area" prompt once: set when the location fix
     * lands outside the area and on Start Hike pressed outside it, cleared by the
     * screen (ClearAreaPrompt) when the prompt has gone.
     */
    val showAreaPrompt: Boolean = false,
    val userEmail: String? = null,
    /** Remote switch (app config `features.planner`); starts at the in-app default. */
    val isPlannerEnabled: Boolean = Feature.PLANNER.default
)
