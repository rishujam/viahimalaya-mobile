package com.via.himalaya.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
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
    private var hasNotificationPermission: Boolean = false

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
    
    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        Log.d("PermissionHandler", "Notification permission granted: $isGranted")
    }

    fun checkAndRequestPermissions() {
        if(shouldShowRationale) {
            Log.d("PermissionHandler", "Should show rationale")
        }
        val hasFine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        hasPreciseLocation = hasFine
        isFullyDenied = !hasFine && !hasCoarse
        shouldShowRationale = !hasFine &&
                    activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
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
    
    fun checkAndRequestNotificationPermission(): Boolean {
        // Notification permission is only required for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            hasNotificationPermission = hasPermission
            
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return false
            }
            return true
        }
        // For Android 12 and below, notification permission is granted by default
        hasNotificationPermission = true
        return true
    }
    
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // No permission needed for older versions
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
}