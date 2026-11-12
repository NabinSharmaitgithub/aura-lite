package com.aura.aura_ui.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aura.aura_ui.MainActivity
import com.aura.aura_ui.R
import com.aura.aura_ui.agent.AgentRunState

object AuraNotifications {
    const val CHANNEL_RUN = "aura_run"
    const val CHANNEL_VOICE = "aura_voice"
    const val CHANNEL_MCP = "aura_mcp"
    const val CHANNEL_APPROVAL = "aura_approval"
    const val CHANNEL_FCM = "aura_fcm"
    const val CHANNEL_AGENT = "aura_agent"

    const val ID_RUN = 1001
    const val ID_WAKE = 1002
    const val ID_MCP = 1003
    const val ID_APPROVAL = 2001
    const val ID_FCM = 2002
    const val ID_INTERRUPTED = 3001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RUN, context.getString(R.string.run_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_VOICE, context.getString(R.string.voice_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_MCP, context.getString(R.string.mcp_channel_name), NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_APPROVAL, context.getString(R.string.approval_channel_name), NotificationManager.IMPORTANCE_HIGH),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_FCM, context.getString(R.string.fcm_channel_name), NotificationManager.IMPORTANCE_DEFAULT),
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_AGENT, context.getString(R.string.agent_channel_name), NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    fun openApp(context: Context, action: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (action != null) this.action = action
        }
        return PendingIntent.getActivity(
            context,
            action?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun fcmNotification(context: Context, title: String, body: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_FCM)
            .setSmallIcon(R.drawable.ic_aura_mark)
            .setContentTitle(title)
            .setContentText(body.take(160))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

    fun interruptedNotification(context: Context, goal: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_aura_mark)
            .setContentTitle("A run was interrupted")
            .setContentText(goal.take(120))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

    fun runNotification(context: Context, state: AgentRunState): Notification {
        val text = when {
            state.paused -> "Paused · ${state.goal.take(60)}"
            state.status == "planning" -> "Planning · step ${state.stepCount}"
            state.status == "verifying" -> "Verifying step ${state.stepCount}"
            state.running -> "Acting · step ${state.stepCount}"
            else -> state.answer?.take(80) ?: "Run finished"
        }
        val pauseIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, RunActionReceiver::class.java).setAction("com.aura.ACTION_TOGGLE_PAUSE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getBroadcast(
            context,
            3,
            Intent(context, RunActionReceiver::class.java).setAction("com.aura.ACTION_STOP_RUN"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_RUN)
            .setSmallIcon(R.drawable.ic_aura_mark)
            .setContentTitle("AURA")
            .setContentText(text)
            .setOngoing(state.running)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .addAction(0, if (state.paused) "Resume" else "Pause", pauseIntent)
            .addAction(0, "Stop", stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun wakeWordNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_VOICE)
            .setSmallIcon(R.drawable.ic_aura_mark)
            .setContentTitle("AURA is listening for “hey aura”")
            .setContentText("Tap to open. Disable in Agent → Voice.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .build()

    fun mcpNotification(context: Context, clients: Int, port: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_MCP)
            .setSmallIcon(R.drawable.ic_aura_mark)
            .setContentTitle("AURA MCP server")
            .setContentText("Listening on port $port · $clients paired")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .build()

    fun approvalNotification(
        context: Context,
        requestId: String,
        tool: String,
        detail: String,
    ): Notification {
        val approve = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            Intent(context, RunActionReceiver::class.java)
                .setAction("com.aura.ACTION_MCP_APPROVE")
                .putExtra("request_id", requestId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deny = PendingIntent.getBroadcast(
            context,
            requestId.hashCode() + 1,
            Intent(context, RunActionReceiver::class.java)
                .setAction("com.aura.ACTION_MCP_DENY")
                .putExtra("request_id", requestId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_APPROVAL)
            .setSmallIcon(R.drawable.ic_aura_mark)
            .setContentTitle("Approve $tool?")
            .setContentText(detail.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .addAction(0, "Allow once", approve)
            .addAction(0, "Deny", deny)
            .build()
    }
}
