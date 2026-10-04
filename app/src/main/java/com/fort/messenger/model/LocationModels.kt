package com.fort.messenger.model

import android.content.Context
import android.content.Intent
import android.net.Uri

enum class LiveLocationDuration(val label: String, val durationMillis: Long) {
    MINUTES_15("15 minutes", 15 * 60 * 1000L),
    HOUR_1("1 hour", 60 * 60 * 1000L),
    HOURS_8("8 hours", 8 * 60 * 60 * 1000L)
}

data class LocationPin(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toLocationMessageText(): String = "$latitude, $longitude"

    fun toGeoUri(): Uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label ?: "Shared Location Pin")})")

    fun openInMapsApp(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, toGeoUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(intent, "Open location pin in Maps").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Fallback to web maps URL
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(webIntent)
        }
    }
}

data class LiveLocationSession(
    val shareId: String,
    val senderUserId: String,
    val senderDisplayName: String,
    val recipientUserId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val startedAt: Long,
    val expiresAt: Long,
    val isStopped: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isExpired: Boolean
        get() = isStopped || System.currentTimeMillis() >= expiresAt

    fun openInMapsApp(context: Context) {
        LocationPin(latitude, longitude, "$senderDisplayName (Live)").openInMapsApp(context)
    }
}
