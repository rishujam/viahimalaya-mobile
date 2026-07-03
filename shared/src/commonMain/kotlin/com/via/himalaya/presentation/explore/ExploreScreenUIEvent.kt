package com.via.himalaya.presentation.explore

sealed class ExploreScreenUIEvent {

    data object OnLoadMore : ExploreScreenUIEvent()

    data object ClearErrorToast : ExploreScreenUIEvent()

    data class OnSearchTrek(val query: String) : ExploreScreenUIEvent()

    data object OnClearSearch : ExploreScreenUIEvent()

}