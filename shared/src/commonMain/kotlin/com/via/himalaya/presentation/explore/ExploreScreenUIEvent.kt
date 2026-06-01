package com.via.himalaya.presentation.explore

import com.via.himalaya.data.models.Trek

sealed class ExploreScreenUIEvent {

    data class OnTrekClick(val trek: Trek) : ExploreScreenUIEvent()

}