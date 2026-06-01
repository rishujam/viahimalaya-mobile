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
import com.via.himalaya.SensorListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class TrekTrackingService : Service() {
    
    companion object {
        const val ACTION_START_TREK = "START_TREK"
        const val ACTION_STOP_TREK = "STOP_TREK"
        const val EXTRA_TREK_NAME = "TREK_NAME"
        
        // Broadcast actions for communication with MainActivity
        const val BROADCAST_START_TREK = "com.via.himalaya.START_TREK"
        const val BROADCAST_STOP_TREK = "com.via.himalaya.STOP_TREK"
        const val BROADCAST_SENSOR_DATA = "com.via.himalaya.SENSOR_DATA"
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "trek_tracking_channel"
        
        fun startTrekTracking(context: Context, trekName: String) {
            val intent = Intent(context, TrekTrackingService::class.java).apply {
                action = ACTION_START_TREK
                putExtra(EXTRA_TREK_NAME, trekName)
            }
            context.startForegroundService(intent)
        }
        
        fun stopTrekTracking(context: Context) {
            val intent = Intent(context, TrekTrackingService::class.java).apply {
                action = ACTION_STOP_TREK
            }
            context.startService(intent)
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var sensorListener: SensorListener
    private lateinit var notificationManager: NotificationManager
    private var currentTrekName: String = ""
    private var pointsRecorded: Int = 0
    
    override fun onCreate() {
        super.onCreate()
        sensorListener = SensorListener(this) { sensorData ->
            // Broadcast sensor data to any listening components
            val intent = Intent(BROADCAST_SENSOR_DATA).apply {
                // Since SensorData is not Serializable/Parcelable, we'll send individual values
                putExtra("accelerometer", sensorData.accelerometer)
                putExtra("gyroscope", sensorData.gyroscope)
                putExtra("magnetometer", sensorData.magnetometer)
                putExtra("pressure", sensorData.pressure ?: 0f)
                putExtra("altBaro", sensorData.altBaro ?: 0f)
                putExtra("battery", sensorData.battery)
                sensorData.location?.let { loc ->
                    putExtra("latitude", loc.lat)
                    putExtra("longitude", loc.lon)
                    putExtra("altitude", loc.altitude ?: 0.0)
                    putExtra("accH", loc.accH ?: 0.0)
                    putExtra("accV", loc.accV ?: 0.0)
                    putExtra("speed", loc.speed ?: 0.0)
                    putExtra("bearing", loc.bearing ?: 0.0)
                }
            }
            sendBroadcast(intent)
        }
        
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TREK -> {
                val trekName = intent.getStringExtra(EXTRA_TREK_NAME) ?: "Unknown Trek"
                startTrekTracking(trekName)
            }
            ACTION_STOP_TREK -> {
                stopTrekTracking()
            }
        }
        
        // Return START_STICKY to restart service if killed by system
        return START_STICKY
    }
    
    private fun startTrekTracking(trekName: String) {
        currentTrekName = trekName
        pointsRecorded = 0
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification("Starting trek...", 0))
        
        // Broadcast start trek event to MainActivity
        val intent = Intent(BROADCAST_START_TREK).apply {
            putExtra(EXTRA_TREK_NAME, trekName)
        }
        sendBroadcast(intent)
        
        // Start sensor collection
        sensorListener.startListening()
    }
    
    private fun stopTrekTracking() {
        // Broadcast stop trek event to MainActivity
        val intent = Intent(BROADCAST_STOP_TREK)
        sendBroadcast(intent)
        
        // Stop sensor collection
        sensorListener.stopListening()
        
        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trek Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous location and sensor tracking for trek recording"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(status: String, pointsCount: Int): Notification {
        // Intent to open main activity when notification is tapped
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Stop action
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, TrekTrackingService::class.java).apply {
                action = ACTION_STOP_TREK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Trek Recording Active")
            .setContentText("$status • $pointsCount points recorded")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation) // Use a location icon
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Trek",
                stopIntent
            )
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    private fun updateNotification(trekId: String?, pointsCount: Int) {
        val status = if (trekId != null) {
            "Recording trek: ${trekId.take(8)}..."
        } else {
            "Trek stopped"
        }
        
        val notification = createNotification(status, pointsCount)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        if (::sensorListener.isInitialized) {
            sensorListener.stopListening()
        }
        
        serviceScope.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}