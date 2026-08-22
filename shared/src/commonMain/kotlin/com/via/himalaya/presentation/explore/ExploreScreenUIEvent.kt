package com.via.himalaya.presentation.explore

sealed class ExploreScreenUIEvent {

    data object OnLoadMore : ExploreScreenUIEvent()

    data object ClearErrorToast : ExploreScreenUIEvent()

    data class OnSearchTrek(val query: String) : ExploreScreenUIEvent()

    data object OnClearSearch : ExploreScreenUIEvent()

    /** Hide button or swipe on the "request a trek" banner. */
    data object OnHideBanner : ExploreScreenUIEvent()

    /** Submit from the request dialog. [text] is already trimmed. */
    data class OnRequestTrek(val text: String) : ExploreScreenUIEvent()

    data object ClearMessageDisplay : ExploreScreenUIEvent()

}