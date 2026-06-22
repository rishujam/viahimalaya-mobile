package com.via.himalaya.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object ViaHimalayaGraph : Route

    @Serializable
    data object Explore : Route

    @Serializable
    data class TrekDetail(val trekId: String, val coordinateUrl: String) : Route

    @Serializable
    data object Profile : Route

    @Serializable
    data object SignIn : Route

}