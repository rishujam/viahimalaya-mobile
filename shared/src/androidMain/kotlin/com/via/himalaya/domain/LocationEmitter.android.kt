package com.via.himalaya.domain

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.via.himalaya.data.models.Loc
import com.via.himalaya.domain.model.LocationResponse
import com.via.himalaya.util.PermissionUtil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidLocationEmitter(
    private val context: Context
) : LocationEmitter {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val settingsClient = LocationServices.getSettingsClient(context)

    companion object {
        private const val TAG = "AndroidLocationEmitter"
    }

    /**
     * Get a single fresh location update.
     * This requests a new location from GPS, not the cached last location.
     * Checks location settings before requesting.
     */
    @SuppressLint("MissingPermission")
    override fun getLocation(
        locationCallback: (LocationResponse) -> Unit
    ) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            0L // Get location immediately
        ).apply {
            setMaxUpdates(1) // Only get one update
            setWaitForAccurateLocation(true) // Wait for accurate location
        }.build()

        // Check location settings first
        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        settingsClient.checkLocationSettings(settingsRequest)
            .addOnSuccessListener {
                Log.d(TAG, "Location settings satisfied, requesting fresh location")
                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val location = locationResult.lastLocation
                        if (location != null) {
                            locationCallback(LocationResponse.Location(location.toLoc()))
                            Log.d(
                                TAG,
                                "Fresh location received: ${location.latitude}, ${location.longitude}"
                            )
                        } else {
                            Log.w(TAG, "Location result was null")
                            locationCallback(LocationResponse.ErrorFetchingLocation("Location result was null"))
                        }
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    Log.e(
                        TAG,
                        "Location settings not satisfied. User needs to enable location.",
                        exception
                    )
                    locationCallback(LocationResponse.SettingDisabled(exception))
                } else {
                    Log.e(TAG, "Failed to check location settings", exception)
                }
            }
    }

    /**
     * Get continuous location updates stream.
     * Use this when trek tracking is active.
     * Checks location settings before starting the stream.
     */
    @SuppressLint("MissingPermission")
    override fun getLiveLocationStream(): Flow<Loc> = callbackFlow {
//        val locationRequest = LocationRequest.Builder(
//            Priority.PRIORITY_HIGH_ACCURACY,
//            10000L // Update interval: 10 seconds
//        ).apply {
//            setMinUpdateIntervalMillis(5000L) // Fastest update interval: 5 seconds
//            setWaitForAccurateLocation(false)
//        }.build()
//
//        // Check location settings first
//        val settingsRequest = LocationSettingsRequest.Builder()
//            .addLocationRequest(locationRequest)
//            .build()
//
//        try {
//            // Use await() to check settings synchronously in the coroutine
//            settingsClient.checkLocationSettings(settingsRequest).await()
//            Log.d(TAG, "Location settings satisfied, starting live location stream")
//
//            val locationCallback = object : LocationCallback() {
//                override fun onLocationResult(locationResult: LocationResult) {
//                    locationResult.lastLocation?.let { location ->
//                        Log.d(
//                            TAG,
//                            "Live location update: ${location.latitude}, ${location.longitude}"
//                        )
//                        trySend(location.toLoc())
//                    }
//                }
//            }
//
//            fusedLocationClient.requestLocationUpdates(
//                locationRequest,
//                locationCallback,
//                Looper.getMainLooper()
//            )
//
//            awaitClose {
//                Log.d(TAG, "Stopping live location stream")
//                fusedLocationClient.removeLocationUpdates(locationCallback)
//            }
//        } catch (exception: Exception) {
//            if (exception is ResolvableApiException) {
//                Log.e(TAG, "Location settings not satisfied for live stream", exception)
////                close(LocationSettingsException("Location settings need to be enabled", exception))
//            } else {
//                Log.e(TAG, "Failed to start live location stream", exception)
//                close(exception)
//            }
//        }
    }

    private fun Location.toLoc(): Loc {
        val accV = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (hasVerticalAccuracy()) verticalAccuracyMeters.toDouble()
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