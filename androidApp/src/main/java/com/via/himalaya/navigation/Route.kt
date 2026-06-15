package com.via.himalaya.navigation

import com.via.himalaya.domain.model.Trek
import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object ViaHimalayaGraph : Route

    @Serializable
    data object Explore : Route

    @Serializable
    data class TrekDetail(val trekId: String) : Route

    @Serializable
    data object Profile : Route

    @Serializable
    data object Trekking : Route

}