package com.via.himalaya.domain.repo

import com.via.himalaya.domain.model.AppConfig
import kotlinx.coroutines.flow.StateFlow

interface AppConfigRepository {

    /**
     * Current config. Starts empty and stays empty until [refresh] succeeds, so
     * every consumer must already handle "nothing configured" - which is the
     * same branch as "this feature is off".
     */
    val config: StateFlow<AppConfig>

    /**
     * Fetches config once. Safe to call more than once; safe to fail.
     *
     * Never throws and never surfaces an error. Config is fetched at launch,
     * when the user has asked for nothing, so a failure has no one to report to
     * and nothing to interrupt.
     */
    suspend fun refresh()
}
