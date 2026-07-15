package com.via.himalaya.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.via.himalaya.MainActivity
import com.via.himalaya.R
import com.via.himalaya.domain.repo.TrekRepository
import com.via.himalaya.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class TrekDownloadService : Service() {

    private val trekRepository: TrekRepository by inject()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null
    
    private lateinit var notificationManager: NotificationManager
    private var notificationBuilder: NotificationCompat.Builder? = null

    companion object {
        const val CHANNEL_ID = "trek_download_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_TREK_ID = "trek_id"
        const val EXTRA_TREK_NAME = "trek_name"
        
        fun startService(context: Context, trekId: String, trekName: String,) {
            val intent = Intent(context, TrekDownloadService::class.java).apply {
                putExtra(EXTRA_TREK_ID, trekId)
                putExtra(EXTRA_TREK_NAME, trekName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val trekId = intent?.getStringExtra(EXTRA_TREK_ID) ?: return START_NOT_STICKY
        val trekName = intent.getStringExtra(EXTRA_TREK_NAME) ?: "Trek"

        // Start foreground service with initial notification
        val notification = createNotification(trekName, 0)
        startForeground(NOTIFICATION_ID, notification)

        // Start download
        downloadJob = serviceScope.launch {
            try {
                downloadTrek(trekId, trekName)
            } catch (e: Exception) {
                updateNotification(trekName, -1, "Download failed: ${e.message}")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun downloadTrek(trekId: String, trekName: String) {
        val trekResult = trekRepository.getTrek(trekId)
        if (trekResult !is Result.Success || trekResult.data == null) {
            updateNotification(trekName, -1, "Failed to fetch trek details")
            stopSelf()
            return
        }
        val trek = trekResult.data
        trek?.let {
            // Download trek offline with progress updates
            trekRepository.downloadTrekOffline(trek) { progress ->
                val progressPercent = (progress * 100).toInt()
                updateNotification(trekName, progressPercent, "Downloading...")
            }
            // Download complete
            updateNotification(trekName, 100, "Download complete!")

        }
        // Stop service after a short delay
        delay(1000)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trek Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows progress of trek downloads"
                setShowBadge(true)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(trekName: String, progress: Int): Notification? {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading $trekName")
            .setContentText(if (progress >= 0) "Progress: $progress%" else "Starting download...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        
        // For Android 15+, set foreground service behavior to show notification immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            notificationBuilder?.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (progress in 0..100) {
            notificationBuilder?.setProgress(100, progress, false)
        } else {
            notificationBuilder?.setProgress(100, 0, true)
        }

        return notificationBuilder?.build()
    }

    private fun updateNotification(trekName: String, progress: Int, message: String? = null) {
        val notification = notificationBuilder?.apply {
            setContentText(message ?: if (progress >= 0) "Progress: $progress%" else "Starting download...")
            if (progress in 0..100) {
                setProgress(100, progress, false)
            } else if (progress == -1) {
                setProgress(0, 0, false)
                setOngoing(false)
            }
        }?.build()

        notification?.let {
            notificationManager.notify(NOTIFICATION_ID, it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
    }
}
