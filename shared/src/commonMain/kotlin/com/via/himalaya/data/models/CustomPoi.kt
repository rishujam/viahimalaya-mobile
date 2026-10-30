package com.via.himalaya.data.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A place the user added from the trek screen: a photo, an optional note and a
 * position. It is written here first and sent to the server when there is a
 * connection, so a camp found at 3,900 m with no signal is not lost.
 *
 * The photo is a file, not a column. A shrunken photo is still several hundred
 * kilobytes, and a row that size can overflow Room's cursor window on read.
 * [photoPath] points at the copy in app-private storage.
 *
 * No foreign key to TrekDetail, for the same reason as TrekPlan: a place a user
 * added has to survive whatever happens to the trek's download.
 */
@Entity(indices = [Index("status")])
data class CustomPoi(
    /**
     * Built when the user taps Save and reused on every retry. The server keys
     * on it, so a request delivered twice is one row, never two.
     */
    @PrimaryKey val id: String,
    val trekId: String,
    val lat: Double,
    val lon: Double,
    /** One of [LocationSource]. */
    val locationSource: String,
    /** Horizontal accuracy of the GPS fix, in metres. Null for a hand-placed pin. */
    val accuracyM: Double? = null,
    val description: String? = null,
    /** One of PoiCategory. Always null for now - there is no picker in the app yet. */
    val category: String? = null,
    val photoPath: String,
    /** Which version of the content terms the user agreed to when saving. */
    val termsVersion: String,
    val createdAt: Long,
    /** One of [CustomPoiStatus]. */
    val status: String = CustomPoiStatus.PENDING,
    /** Tries that reached no verdict. Diagnostic only - nothing gives up on a count. */
    val attempts: Int = 0,
    val lastError: String? = null
)

object CustomPoiStatus {
    /** Waiting for a connection, or for the next try. */
    const val PENDING = "PENDING"

    /** The server has it. The photo file is deleted at this point. */
    const val UPLOADED = "UPLOADED"

    /**
     * The server refused this exact request and always will (a point outside
     * the trekking area, say). Never retried. The photo file is kept, so that a
     * mistake on our side - a server rule that was wrong - does not destroy
     * what someone took the trouble to send.
     */
    const val FAILED = "FAILED"
}

/** How the position of a [CustomPoi] was chosen. The same two words go to the server. */
object LocationSource {
    /** The phone's own fix: "use my current location". */
    const val GPS = "gps"

    /** A pin the user placed on the map by hand. */
    const val MAP = "map"
}

/**
 * The `meta` part of POST /api/custom-pois. The photo travels beside it as the
 * `photo` part.
 *
 * There is deliberately no user field: the server takes the author from the
 * login token, so the body cannot name anyone.
 */
@Serializable
data class CustomPoiUploadMeta(
    val id: String,
    val trekId: String,
    val lat: Double,
    val lon: Double,
    val locationSource: String,
    val accuracyM: Double? = null,
    val description: String? = null,
    val category: String? = null,
    val termsVersion: String
)

fun CustomPoi.toUploadMeta() = CustomPoiUploadMeta(
    id = id,
    trekId = trekId,
    lat = lat,
    lon = lon,
    locationSource = locationSource,
    accuracyM = accuracyM,
    description = description,
    category = category,
    termsVersion = termsVersion
)
