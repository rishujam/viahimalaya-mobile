package com.via.himalaya.data.remote

import com.via.himalaya.domain.Tracker
import dev.gitlive.firebase.analytics.FirebaseAnalytics

class FirebaseTracker(
    private val tracker: FirebaseAnalytics
) : Tracker {

    override fun track(event: String, params: Map<String, String>) {
        tracker.logEvent(name = event, params)
    }

}