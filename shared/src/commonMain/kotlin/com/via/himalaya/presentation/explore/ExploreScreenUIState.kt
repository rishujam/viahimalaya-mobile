package com.via.himalaya.presentation.explore

import com.via.himalaya.data.models.Trek
import com.via.himalaya.domain.model.AppBanner

data class ExploreScreenUIState(
    val treks: List<Trek> = emptyList(),
    val tempTreks: List<Trek> = emptyList(),
    val errorToast: String? = null,
    val messageDisplay: String? = null,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val page: Int = 0,
    val seed: String? = null,
    val hasNextPage: Boolean = true,

    /**
     * Banner supplied by /api/app-config, or null when there is none.
     *
     * Null covers three cases on purpose - config has not arrived yet, the
     * server has disabled the banner, and the fetch failed - because all three
     * mean the same thing to this screen. There is no separate "should I show
     * it" boolean: a second field would be a second source of truth, and it
     * would render an empty banner in the window before config arrives.
     */
    val banner: AppBanner? = null,

    /**
     * Whether the user has dismissed the banner this session, by the hide
     * button or by swiping it away. Deliberately not persisted — it lives as
     * long as this ViewModel, so it survives navigating to a trek and back but
     * resets on a fresh launch.
     */
    val isBannerHidden: Boolean = false
)
