package com.via.himalaya.data.models

import kotlinx.serialization.Serializable

/**
 * Body of POST /api/feedback.
 *
 * [feedbackId] is "<email>/<epochMillis>", built once when the user submits and
 * reused on every retry - that is what makes the write idempotent server-side,
 * so a request delivered twice does not become two rows. Generating a fresh id
 * per attempt would defeat it.
 */
@Serializable
data class FeedbackRequest(
    val feedbackId: String,
    val feedback: String
)
