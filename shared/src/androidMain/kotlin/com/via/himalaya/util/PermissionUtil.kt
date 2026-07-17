package com.via.himalaya.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlin.text.get

object PermissionUtil {

    const val PERMISSION_NOTIFICATION = Manifest.permission.POST_NOTIFICATIONS
    const val PERMISSION_LOCATION_PRECISE = Manifest.permission.ACCESS_FINE_LOCATION
    const val PERMISSION_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun hasLocationPermission(context: Context): Boolean {
        return hasPermission(context, PERMISSION_LOCATION) &&
                hasPermission(context, PERMISSION_LOCATION_PRECISE)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, PERMISSION_NOTIFICATION)
        } else {
            true
        }
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}