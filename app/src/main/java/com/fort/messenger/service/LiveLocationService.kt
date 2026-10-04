package com.fort.messenger.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.fort.messenger.MainActivity
import com.fort.messenger.data.local.FortDatabase
import com.fort.messenger.data.remote.FortBackendFactory
import com.fort.messenger.data.repository.FortRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LiveLocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var repository: FortRepository

    private var shareId: String = ""
    private var senderUserId: String = ""
    private var senderDisplayName: String = ""
    private var recipientUserId: String = ""
    private var expiresAt: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        repository = FortRepository(
            database = FortDatabase.getInstance(applicationContext),
            remoteBackend = FortBackendFactory.createBackend(applicationContext)
        )
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SHARING) {
            stopSharingAndTerminate()
            return START_NOT_STICKY
        }

        shareId = intent?.getStringExtra(EXTRA_SHARE_ID) ?: ""
        senderUserId = intent?.getStringExtra(EXTRA_SENDER_USER_ID) ?: ""
        senderDisplayName = intent?.getStringExtra(EXTRA_SENDER_DISPLAY_NAME) ?: "User"
        recipientUserId = intent?.getStringExtra(EXTRA_RECIPIENT_USER_ID) ?: ""
        expiresAt = intent?.getLongExtra(EXTRA_EXPIRES_AT, 0L) ?: 0L

        if (shareId.isBlank() || recipientUserId.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        startLocationUpdates()

        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMaxUpdateDelayMillis(15_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val now = System.currentTimeMillis()

                if (now >= expiresAt) {
                    stopSharingAndTerminate()
                    return
                }

                serviceScope.launch {
                    repository.updateLiveLocation(
                        shareId = shareId,
                        senderUserId = senderUserId,
                        senderDisplayName = senderDisplayName,
                        recipientUserId = recipientUserId,
                        lat = location.latitude,
                        lng = location.longitude,
                        accuracy = location.accuracy,
                        startedAt = now,
                        expiresAt = expiresAt
                    )
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun stopSharingAndTerminate() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}

        serviceScope.launch {
            if (shareId.isNotBlank()) {
                repository.stopLiveLocationSharing(shareId, senderUserId, recipientUserId)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveLocationService::class.java).apply {
            action = ACTION_STOP_SHARING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fort Live Location Sharing")
            .setContentText("Sharing real-time encrypted location with chosen contact")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Location Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active private live location sharing status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "fort_live_location"
        const val NOTIFICATION_ID = 4040
        const val ACTION_STOP_SHARING = "com.fort.messenger.ACTION_STOP_LIVE_LOCATION"
        const val EXTRA_SHARE_ID = "share_id"
        const val EXTRA_SENDER_USER_ID = "sender_user_id"
        const val EXTRA_SENDER_DISPLAY_NAME = "sender_display_name"
        const val EXTRA_RECIPIENT_USER_ID = "recipient_user_id"
        const val EXTRA_EXPIRES_AT = "expires_at"

        fun start(
            context: Context,
            shareId: String,
            senderUserId: String,
            senderDisplayName: String,
            recipientUserId: String,
            expiresAt: Long
        ) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                putExtra(EXTRA_SHARE_ID, shareId)
                putExtra(EXTRA_SENDER_USER_ID, senderUserId)
                putExtra(EXTRA_SENDER_DISPLAY_NAME, senderDisplayName)
                putExtra(EXTRA_RECIPIENT_USER_ID, recipientUserId)
                putExtra(EXTRA_EXPIRES_AT, expiresAt)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveLocationService::class.java).apply {
                action = ACTION_STOP_SHARING
            }
            context.startService(intent)
        }
    }
}
