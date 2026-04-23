package com.via.himalaya.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionHandler(private val activity: ComponentActivity) {
    
    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()
    
    data class PermissionStatus(
        val hasLocationPermission: Boolean = false,
        val hasApproximateLocationPermission: Boolean = false,
        val hasBackgroundLocationPermission: Boolean = false,
        val permissionDenied: Boolean = false,
        val shouldShowRationale: Boolean = false
    )
    
    private val locationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasFineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val hasCoarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        _permissionStatus.value = _permissionStatus.value.copy(
            hasLocationPermission = hasFineLocation,
            hasApproximateLocationPermission = hasCoarseLocation,
            permissionDenied = !hasFineLocation && !hasCoarseLocation,
            shouldShowRationale = !hasFineLocation &&
                activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        )
        
        // If precise location permission is granted and we're on Android 10+, request background location
        if (hasFineLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestBackgroundLocationPermission()
        }
    }
    
    private val backgroundLocationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        _permissionStatus.value = _permissionStatus.value.copy(
            hasBackgroundLocationPermission = granted
        )
    }
    
    fun checkAndRequestPermissions() {
        val hasLocationPermission = hasLocationPermission()
        val hasBackgroundLocationPermission = hasBackgroundLocationPermission()
        
        _permissionStatus.value = PermissionStatus(
            hasLocationPermission = hasLocationPermission,
            hasApproximateLocationPermission = hasApproximateLocationPermission(),
            hasBackgroundLocationPermission = hasBackgroundLocationPermission
        )
        
        when {
            !hasLocationPermission -> {
                requestLocationPermissions()
            }
            hasLocationPermission && !hasBackgroundLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                requestBackgroundLocationPermission()
            }
        }
    }
    
    private fun requestLocationPermissions() {
        // Request both together so Android 12+ can grant either precise or approximate location.
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasApproximateLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older versions
        }
    }
    
    fun getPermissionStatusText(): String {
        val status = _permissionStatus.value
        return when {
            !status.hasLocationPermission && status.hasApproximateLocationPermission ->
                "⚠️ Precise location permission required for trek tracking"
            !status.hasLocationPermission -> "❌ Precise location permission required for trek tracking"
            !status.hasBackgroundLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                "⚠️ Background location recommended for continuous tracking"
            else -> "✅ All permissions granted"
        }
    }
}
