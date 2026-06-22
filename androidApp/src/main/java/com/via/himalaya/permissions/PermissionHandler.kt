package com.via.himalaya.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.content.ContextCompat
import com.mapbox.maps.extension.style.expressions.dsl.generated.has
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionHandler(private val activity: ComponentActivity) {

    private var hasPreciseLocation: Boolean = false
    private var isFullyDenied: Boolean = false
    private var shouldShowRationale: Boolean = false

    // Launch the dual prompt required for Android 12+
    private val locationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        hasPreciseLocation = hasFineLocation
        isFullyDenied = !hasFineLocation && !hasCoarseLocation
        shouldShowRationale = !hasFineLocation &&
                activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun checkAndRequestPermissions() {
        val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        hasPreciseLocation = hasFine
        isFullyDenied = !hasFine && !hasCoarse
        shouldShowRationale = !hasFine &&
                    activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

        // If we don't have precise location, trigger the system dialog
        if (!hasFine) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun hasPreciseLocationPermission(): Boolean {
        return hasPreciseLocation
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
}