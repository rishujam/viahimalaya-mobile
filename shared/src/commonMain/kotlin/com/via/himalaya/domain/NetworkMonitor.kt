package com.via.himalaya.domain

/**
 * Whether the phone has a working internet connection right now.
 *
 * A snapshot, not a stream: callers ask just before they would use the network.
 * It is advice, not a guarantee - a connection can drop a moment later - so
 * whatever it gates must still cope with a request that fails.
 */
fun interface NetworkMonitor {
    fun isOnline(): Boolean
}
