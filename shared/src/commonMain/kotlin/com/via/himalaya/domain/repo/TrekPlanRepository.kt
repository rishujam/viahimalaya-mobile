package com.via.himalaya.domain.repo

import com.via.himalaya.data.models.PlannedDay
import com.via.himalaya.data.models.TrekPlan

/**
 * Saved itineraries, local only.
 *
 * Nothing here touches the network. A plan is personal, it is wanted most on a
 * trail with no signal, and it is small enough that syncing would cost more than
 * it is worth. If plans ever need to follow a user between devices, that becomes
 * an implementation of this same interface.
 */
interface TrekPlanRepository {

    /** Plans for one trek, newest first. */
    suspend fun getPlans(trekId: String): List<TrekPlan>

    /** Creates a plan and returns the id Room assigned it. */
    suspend fun createPlan(trekId: String, days: List<PlannedDay>): Long

    suspend fun deletePlan(planId: Long)
}
