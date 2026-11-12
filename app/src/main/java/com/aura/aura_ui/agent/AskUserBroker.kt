package com.aura.aura_ui.agent

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aura.aura_ui.R
import com.aura.aura_ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/**
 * HITL ask_user: lets the running agent pause and ask the user a question, then blocks until
 * the user answers or the timeout elapses. The UI collects [pending] and calls [answer] /
 * [dismiss]. When the user is not in the app, a high-priority notification deep-links into the
 * app with the question visible.
 */
@Singleton
class AskUserBroker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Pending(
        val id: String,
        val question: String,
        val options: List<String>,
        val askedAt: Long,
    )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    private var reply: CompletableDeferred<String>? = null

    /** Suspend until the user answers. Returns a placeholder when declined or timed out. */
    suspend fun ask(question: String, options: List<String>, timeoutSeconds: Long): String {
        // Only one question in flight; never clobber an existing one.
        if (_pending.value != null) return "A question is already open; answer it in the app first."

        val deferred = CompletableDeferred<String>()
        reply = deferred
        val request = Pending(
            id = System.currentTimeMillis().toString(),
            question = question,
            options = options,
            askedAt = System.currentTimeMillis(),
        )
        _pending.value = request
        postNotification(request)
        return try {
            withTimeout(timeoutSeconds.coerceAtLeast(5) * 1000L) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            "(no answer within ${timeoutSeconds}s)"
        } finally {
            reply = null
            _pending.value = null
        }
    }

    fun answer(answer: String) {
        reply?.complete(answer.trim())
    }

    fun dismiss() {
        reply?.complete("(dismissed)")
    }

    private fun postNotification(pending: Pending) {
        val channel = "aura_agent"
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(channel, "Agent questions", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tap = PendingIntent.getActivity(
            context,
            pending.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_aura_mark)
            .setContentTitle("AURA asks")
            .setContentText(pending.question.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(pending.question))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .build()
        runCatching { manager.notify(ASK_NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val ASK_NOTIFICATION_ID = 7001
    }
}
