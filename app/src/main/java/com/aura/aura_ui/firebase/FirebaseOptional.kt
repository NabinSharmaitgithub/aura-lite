package com.aura.aura_ui.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException

/**
 * AURA is offline-first. Firebase is compiled in but only becomes live when a
 * `google-services.json` is present at build time, which registers a [FirebaseApp]. Every entry
 * point checks [available] first and degrades to a no-op — never a crash — when it is missing.
 */
object FirebaseOptional {
    const val SNAPSHOT_VERSION = 1

    /** True when a FirebaseApp was registered (google-services.json was bundled at build time). */
    fun available(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    fun requireApp(context: Context): FirebaseApp {
        if (!available(context)) throw FirebaseException("Firebase is not configured on this build")
        return FirebaseApp.getInstance()
    }
}
