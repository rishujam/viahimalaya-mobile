package com.via.himalaya.data.repository

import com.via.himalaya.data.local.TrekPlanDao
import com.via.himalaya.data.models.PlannedDay
import com.via.himalaya.data.models.TrekPlan
import com.via.himalaya.domain.repo.TrekPlanRepository
import kotlinx.datetime.Clock

class TrekPlanRepositoryImpl(
    private val trekPlanDao: TrekPlanDao
) : TrekPlanRepository {

    override suspend fun getPlans(trekId: String): List<TrekPlan> =
        trekPlanDao.getPlansForTrek(trekId)

    override suspend fun createPlan(trekId: String, days: List<PlannedDay>): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        return trekPlanDao.insert(
            TrekPlan(
                trekId = trekId,
                days = days,
                createdAt = now,
                // Same as createdAt on insert. Kept separate because the read
                // orders by updatedAt, and an edit later should reorder without
                // losing when the plan was first made.
                updatedAt = now
            )
        )
    }

    override suspend fun deletePlan(planId: Long) = trekPlanDao.deletePlan(planId)
}
