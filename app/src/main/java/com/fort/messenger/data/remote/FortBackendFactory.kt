package com.fort.messenger.data.remote

import android.content.Context

object FortBackendFactory {

    @Volatile
    private var testBackendOverride: FortRemoteBackend? = null

    /**
     * For unit tests only.
     */
    fun setTestBackend(backend: FortRemoteBackend?) {
        testBackendOverride = backend
    }

    /**
     * Creates or provides the active backend.
     * Uses production backend unless overridden for tests.
     */
    fun createBackend(context: Context): FortRemoteBackend {
        return testBackendOverride ?: FirebaseRemoteBackend(context)
    }
}
