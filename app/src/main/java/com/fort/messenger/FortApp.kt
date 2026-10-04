package com.fort.messenger

import android.app.Application
import com.google.firebase.FirebaseApp

class FortApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // FirebaseApp returns null when google-services.json is not configured; the sign-in UI
        // then shows the required setup message instead of launching a nonfunctional demo backend.
        runCatching { FirebaseApp.initializeApp(this) }
    }
}
