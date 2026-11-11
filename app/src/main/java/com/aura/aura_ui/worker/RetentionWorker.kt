package com.aura.aura_ui.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.data.session.SessionLogStore
import java.io.File
import java.util.concurrent.TimeUnit

/** Prunes session traces, screenshots and the MCP audit log on the user's retention window. */
class RetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AuraSettings(applicationContext)
        val days = settings.logRetentionDays
        val removed = SessionLogStore(applicationContext).prune(days)
        val audit = File(applicationContext.filesDir, "mcp/audit.jsonl")
        if (audit.exists()) {
            val cutoff = System.currentTimeMillis() - days * 86_400_000L
            val kept = audit.readLines().filter { line ->
                val t = Regex("\"t\":(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
                t != null && t >= cutoff
            }
            audit.writeText(kept.joinToString("\n", postfix = if (kept.isEmpty()) "" else "\n"))
        }
        // Screenshots are single-use: retain none of them past a retention pass.
        runCatching { File(applicationContext.filesDir, "screen/current.png").delete() }
        return Result.success()
    }

    companion object {
        private const val NAME = "aura_retention"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.NONE)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
