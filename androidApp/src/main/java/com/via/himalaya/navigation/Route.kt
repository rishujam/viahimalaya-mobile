package com.via.himalaya.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object ViaHimalayaGraph : Route

    @Serializable
    data object Explore : Route

    @Serializable
    data class TrekDetail(val trekId: String, val coordinateUrl: String) : Route

    /**
     * Multi-day planning for a trek. Carries the same two arguments as
     * [TrekDetail] because it loads the same geometry - planning is a different
     * question about the same trail, not a different trail.
     */
    @Serializable
    data class TrekPlan(val trekId: String, val coordinateUrl: String) : Route

    @Serializable
    data object Profile : Route

    @Serializable
    data object SignIn : Route

    @Serializable
    data object DownloadedTrek : Route

    /**
     * About and data credits. Carries the OpenStreetMap and Copernicus
     * attribution their licences require, so it has to stay reachable.
     */
    @Serializable
    data object About : Route

}