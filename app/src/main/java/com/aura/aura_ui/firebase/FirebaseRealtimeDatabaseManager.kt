package com.aura.aura_ui.firebase

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Fault-tolerant wrapper around Firebase Realtime Database.
 *
 * Like all Firebase services in AURA, this is 100% opt-in and checks [FirebaseOptional.available]
 * first, cleanly degrading to no-ops when Firebase is not configured on the build.
 */
@Singleton
class FirebaseRealtimeDatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuthManager,
) {
    private val database: FirebaseDatabase?
        get() = if (FirebaseOptional.available(context)) {
            runCatching {
                FirebaseDatabase.getInstance("https://aura-android-use-nabin-default-rtdb.firebaseio.com")
            }.getOrNull()
        } else {
            null
        }

    val isAvailable: Boolean
        get() = database != null

    /**
     * Attaches live online/offline presence handling using the `.info/connected` special location.
     */
    fun attachPresence(deviceId: String) {
        val db = database ?: return
        val uid = auth.currentUser?.uid ?: return

        runCatching {
            val userPresenceRef = db.getReference("presence/$uid")
            val connectedRef = db.getReference(".info/connected")

            connectedRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    if (connected) {
                        val offlineMap = mapOf(
                            "state" to "offline",
                            "deviceId" to deviceId,
                            "lastChanged" to ServerValue.TIMESTAMP,
                        )
                        userPresenceRef.onDisconnect().setValue(offlineMap)

                        val onlineMap = mapOf(
                            "state" to "online",
                            "deviceId" to deviceId,
                            "lastChanged" to ServerValue.TIMESTAMP,
                        )
                        userPresenceRef.setValue(onlineMap)
                    }
                }

                override fun onCancelled(error: DatabaseError) = Unit
            })
        }
    }

    /**
     * Publishes current device / agent live status to `users/$uid/live_status`.
     */
    suspend fun updateLiveStatus(status: String, details: Map<String, Any?> = emptyMap()): Result<Unit> =
        runCatching {
            val db = database ?: return@runCatching
            val uid = auth.currentUser?.uid ?: return@runCatching

            val payload = mutableMapOf<String, Any?>(
                "status" to status,
                "updatedAt" to ServerValue.TIMESTAMP,
            )
            payload.putAll(details)

            db.getReference("users/$uid/live_status").setValue(payload).await()
        }

    /**
     * Listens to live status updates under `users/$uid/live_status`.
     */
    fun observeLiveStatus(): Flow<Map<String, Any?>?> = callbackFlow {
        val db = database
        val uid = auth.currentUser?.uid
        if (db == null || uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val ref = db.getReference("users/$uid/live_status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                val value = snapshot.value as? Map<String, Any?>
                trySend(value)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
