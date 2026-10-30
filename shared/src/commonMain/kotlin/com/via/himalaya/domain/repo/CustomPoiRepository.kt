package com.via.himalaya.domain.repo

import com.via.himalaya.data.models.CustomPoi

interface CustomPoiRepository {

    /**
     * Writes a place to the phone. Never touches the network, so it works with
     * no signal - which is exactly where a place worth adding tends to be.
     */
    suspend fun save(poi: CustomPoi)

    /**
     * Sends whatever is waiting, if the phone is online and someone is signed
     * in. Returns at once: the upload runs on a scope that outlives any screen,
     * so leaving the screen does not cancel it.
     *
     * Cheap when there is nothing to do - one local query - so callers can use it
     * freely: after a save, and whenever the app comes to the foreground. A place
     * that could not be sent is simply tried again the next time.
     */
    fun requestSync()

    /** What [requestSync] runs. Suspends until the run is over; one run at a time. */
    suspend fun syncPending()
}
