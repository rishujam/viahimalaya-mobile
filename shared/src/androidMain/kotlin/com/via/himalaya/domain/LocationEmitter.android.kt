package com.via.himalaya.domain

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.via.himalaya.data.models.Loc
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidLocationEmitter(
    private val context: Context
) : LocationEmitter {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getLocation(): Loc? = suspendCancellableCoroutine { continuation ->
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    continuation.resume(location.toLoc())
                } else {
                    // If last location is null, request a fresh location
                    requestSingleLocation { freshLocation ->
                        continuation.resume(freshLocation)
                    }
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    override fun getLiveLocationStream(): Flow<Loc> = callbackFlow {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L // Update interval: 10 seconds
        ).apply {
            setMinUpdateIntervalMillis(5000L) // Fastest update interval: 5 seconds
            setWaitForAccurateLocation(false)
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    trySend(location.toLoc())
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleLocation(callback: (Loc?) -> Unit) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            0L // Get location immediately
        ).apply {
            setMaxUpdates(1) // Only get one update
        }.build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                callback(location?.toLoc())
                fusedLocationClient.removeLocationUpdates(this)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun Location.toLoc(): Loc {
        val accV = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(hasVerticalAccuracy()) verticalAccuracyMeters.toDouble()
            else null
        } else null
        return Loc(
            lat = latitude,
            lon = longitude,
            altitude = if (hasAltitude()) altitude else null,
            accH = if (hasAccuracy()) accuracy.toDouble() else null,
            accV = accV,
            speed = if (hasSpeed()) speed.toDouble() else null,
            bearing = if (hasBearing()) bearing.toDouble() else null
        )
    }
}
