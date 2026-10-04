package com.fort.messenger

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class FortApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ensureFirebaseInitialized(this)
    }

    companion object {
        fun ensureFirebaseInitialized(context: Context): FirebaseApp? {
            val apps = FirebaseApp.getApps(context)
            if (apps.isNotEmpty()) {
                return apps.first()
            }

            // 1. Try standard initialization from generated resources (google-services.json)
            val resourceApp = runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
            if (resourceApp != null) {
                return resourceApp
            }

            // 2. Programmatic initialization for fort-chat-f3308
            return runCatching {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:259638681713:android:fc6d3434e8a1e0a149bbd0")
                    .setApiKey("AIzaSyBBD7aMf5z1OwJOt5HSGmW4RJxmcMi5nt0")
                    .setProjectId("fort-chat-f3308")
                    .setStorageBucket("fort-chat-f3308.firebasestorage.app")
                    .setGcmSenderId("259638681713")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }.getOrNull()
        }
    }
}
