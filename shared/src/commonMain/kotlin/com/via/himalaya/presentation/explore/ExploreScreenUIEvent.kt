package com.via.himalaya.presentation.explore

sealed class ExploreScreenUIEvent {

    data object OnLoadMore : ExploreScreenUIEvent()

    data object ClearErrorMessage : ExploreScreenUIEvent()

}