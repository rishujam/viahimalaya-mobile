package com.via.himalaya.domain.repo

import com.via.himalaya.util.Result

interface FeedbackRepository {

    /**
     * Sends one piece of free-text feedback.
     *
     * [feedbackId] must be stable across retries of the same submission - the
     * server keys on it to collapse duplicate deliveries, so a regenerated id
     * would write a second row for the same thing.
     */
    suspend fun submitFeedback(feedbackId: String, feedback: String): Result<Unit>
}
