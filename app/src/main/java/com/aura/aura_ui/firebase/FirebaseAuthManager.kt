package com.aura.aura_ui.firebase

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Thin, fault-tolerant wrapper around FirebaseAuth. All Firebase entry points check
 * [FirebaseOptional.available] first; every call site can use these without crashing an
 * offline-only build.
 */
@Singleton
class FirebaseAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val auth: FirebaseAuth?
        get() = if (FirebaseOptional.available(context)) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null

    val signedIn: Boolean get() = auth?.currentUser != null

    val currentUser: FirebaseUser? get() = auth?.currentUser

    val email: String? get() = currentUser?.email

    /** Subscribes to auth state changes; returns an unsubscribe lambda. */
    fun observe(onChange: (FirebaseUser?) -> Unit): () -> Unit {
        val firebase = auth ?: return { onChange(null) }
        val listener = FirebaseAuth.AuthStateListener { onChange(it.currentUser) }
        firebase.addAuthStateListener(listener)
        onChange(firebase.currentUser)
        return { runCatching { firebase.removeAuthStateListener(listener) } }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val firebase = auth ?: throw IllegalStateException("Firebase is not configured on this build")
            require(email.isNotBlank() && password.isNotBlank()) { "Email and password are required" }
            firebase.signInWithEmailAndPassword(email.trim(), password).await().user
                ?: throw IllegalStateException("Sign-in returned no user")
        }

    suspend fun createAccountWithEmail(email: String, password: String): Result<FirebaseUser> =
        runCatching {
            val firebase = auth ?: throw IllegalStateException("Firebase is not configured on this build")
            require(email.isNotBlank() && password.length >= 6) { "Password must be at least 6 characters" }
            firebase.createUserWithEmailAndPassword(email.trim(), password).await().user
                ?: throw IllegalStateException("Account creation returned no user")
        }

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> =
        runCatching {
            val firebase = auth ?: throw IllegalStateException("Firebase is not configured on this build")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebase.signInWithCredential(credential).await().user
                ?: throw IllegalStateException("Google sign-in returned no user")
        }

    fun signOut() {
        runCatching { auth?.signOut() }
    }
}
