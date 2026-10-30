package com.via.himalaya.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The app's half of the contract written at the top of the server route. If the
 * route's list of answers changes, this is what has to change with it.
 */
class UploadOutcomeTest {

    @Test
    fun storedAndAlreadyStoredAreBothDone() {
        assertEquals(UploadOutcome.SENT, UploadOutcome.of(201))
        assertEquals(UploadOutcome.SENT, UploadOutcome.of(200))
    }

    @Test
    fun refusalsThatCanNeverSucceedAreGivenUpOn() {
        for (status in listOf(400, 404, 409, 413, 415, 422)) {
            assertEquals(UploadOutcome.GIVE_UP, UploadOutcome.of(status), "HTTP $status")
        }
    }

    @Test
    fun everythingElseIsKeptForAnotherTime() {
        // 401 no valid login, 429 daily limit, 5xx server trouble, and anything
        // unexpected - none of which is the place's fault.
        for (status in listOf(401, 403, 408, 429, 500, 502, 503, 504, 301, 302, 204, 0)) {
            assertEquals(UploadOutcome.TRY_LATER, UploadOutcome.of(status), "HTTP $status")
        }
    }
}
