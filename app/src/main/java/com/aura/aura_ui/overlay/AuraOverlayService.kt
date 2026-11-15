package com.aura.aura_ui.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import com.aura.aura_ui.agent.AgentRuntime
import com.aura.aura_ui.agent.hitl.AskUserBroker
import com.aura.aura_ui.agent.ledger.PlanStepStatus
import com.aura.aura_ui.agent.runtime.StatusNarrator
import com.aura.aura_ui.notifications.AuraNotificationListenerService
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.services.AgentStatusRegistry
import com.aura.aura_ui.services.AssistantForegroundService
import com.aura.aura_ui.state.PillState
import com.aura.aura_ui.voice.VoiceEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Floating assistant surface — Ported directly from Figma project "aura-wake-screen" (node 0:1).
 *
 * Displays over any other application on the phone (TYPE_APPLICATION_OVERLAY) while the agent
 * is actively executing tasks or conducting interactive notification conversations.
 *
 * Displays:
 * - Rectangle 3 (Top Card): Frosted light grey card with JetBrains Mono ExtraBold typography
 *   alternating smoothly between:
 *   1. User Command: Initial instruction/goal requested by user
 *   2. Live Caption: Current action / tool / thought being executed
 *   3. To-do List: Plan steps checklist with [✓], [•], [ ] markers
 * - Rectangle 1 (Bottom Pill): Black capsule pill hosting 16 animated white bars oscillating
 *   dynamically to audio amplitude.
 *
 * Interactive:
 * - Tapping the bottom black pill opens the mic for barge-in / mid-task user instruction even
 *   while the agent is actively executing a task.
 * - Handles conversational read & reply when device is unlocked with unread notifications.
 */
@AndroidEntryPoint
class AuraOverlayService : Service() {

    @Inject lateinit var voice: VoiceEngine
    @Inject lateinit var runtime: AgentRuntime

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var view: OverlayPillView? = null
    private var currentGoal: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private var notificationJob: Job? = null

    fun updateLiveNotification(agent: String, output: String) = Companion.updateLiveNotification(agent, output)
    fun updateLiveNotificationProgress(agent: String, step: String) = Companion.updateLiveNotificationProgress(agent, step)
    fun updateLiveNotificationProgress(
        goal: String,
        current: Int,
        total: Int,
        isComplete: Boolean = false,
        isAborted: Boolean = false,
    ) = Companion.updateLiveNotificationProgress(goal, current, total, isComplete, isAborted)
    fun endVoiceConversation() = Companion.endVoiceConversation()

    override fun onCreate() {
        super.onCreate()
        instance = this
        AuraNotifications.ensureChannels(this)
        startForeground(AuraNotifications.ID_RUN, AuraNotifications.wakeWordNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay — SYSTEM_ALERT_WINDOW permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_HIDE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_TASK -> {
                val goal = intent.getStringExtra(EXTRA_TASK_GOAL).orEmpty()
                if (goal.isNotBlank()) {
                    currentGoal = goal
                }
                showFigmaOverlay()
                view?.setTaskData(currentGoal, isRunning = true)
            }
            ACTION_SHOW_AND_LISTEN -> {
                showFigmaOverlay()
                AssistantForegroundService.listen(this)
            }
            ACTION_NOTIFICATION_INTERACTION -> {
                val key = intent.getStringExtra(EXTRA_NOTIF_KEY).orEmpty()
                val app = intent.getStringExtra(EXTRA_NOTIF_APP).orEmpty()
                val sender = intent.getStringExtra(EXTRA_NOTIF_SENDER).orEmpty()
                val text = intent.getStringExtra(EXTRA_NOTIF_TEXT).orEmpty()
                val replyAction = intent.getStringExtra(EXTRA_NOTIF_REPLY_ACTION)
                runNotificationConversation(key, app, sender, text, replyAction)
            }
            else -> {
                showFigmaOverlay()
            }
        }

        observeAll()
        return START_STICKY
    }

    private fun showFigmaOverlay() {
        if (view != null) return

        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val pill = OverlayPillView(this)
        pill.onTapPill = {
            if (voice.listening.value) {
                voice.cancelListening()
                pill.update(pillState(), "Tap to talk", "")
            } else {
                // Allows user to give instruction / barge in even during task execution
                AssistantForegroundService.listen(this)
            }
        }
        pill.onLongPressPill = { stopSelf() }
        pill.onDismiss = {
            if (!runtime.isRunning && notificationJob?.isActive != true) {
                stopSelf()
            }
        }

        if (currentGoal.isNotBlank()) {
            pill.setTaskData(currentGoal, runtime.isRunning)
        }

        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (24 * density).toInt()
        }

        runCatching { manager.addView(pill, params) }
        view = pill
    }

    /**
     * Interactive conversational flow triggered on device unlock:
     * 1. Aura wakes: "Hey boss, you've got a notification from <App>. May I read it for you?"
     * 2. If user says yes: Aura reads content and asks "May I reply to it?"
     * 3. If user says yes: Aura asks "What would you like to reply?"
     * 4. User speaks reply -> Aura sends message via notification direct reply and confirms!
     */
    private fun runNotificationConversation(
        key: String,
        appName: String,
        sender: String,
        messageText: String,
        replyActionTitle: String?,
    ) {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            showFigmaOverlay()

            // Step 1: Wake & Prompt
            val promptText = "Hey boss, you've got a notification from $appName. May I read it for you?"
            view?.update(PillState.SPEAKING, "AURA · NOTIFICATION", promptText)
            view?.updateLiveCaption(promptText)
            voice.speakAndAwait(promptText)

            // Listen for user response (Yes / No)
            view?.update(PillState.LISTENING, "Listening for your choice…", "")
            val resp1 = voice.listen(timeoutMs = 12000, force = true).getOrNull()?.trim()?.lowercase().orEmpty()

            val isYes1 = resp1.contains("yes") || resp1.contains("yeah") || resp1.contains("sure") ||
                resp1.contains("read") || resp1.contains("ok") || resp1.contains("okay") ||
                resp1.contains("yup") || resp1.contains("please") || resp1.contains("go ahead")

            if (!isYes1) {
                val dismissMsg = "Alright, boss."
                view?.update(PillState.SPEAKING, "AURA", "Dismissed")
                view?.updateLiveCaption("Dismissed")
                voice.speakAndAwait(dismissMsg)
                delay(1200)
                if (!runtime.isRunning) stopSelf()
                return@launch
            }

            // Step 2: Read notification & ask to reply
            val whoSays = if (sender.isNotBlank() && !sender.equals(appName, ignoreCase = true)) {
                "$sender says"
            } else {
                "It says"
            }
            val readText = "$whoSays: \"$messageText\""
            val askReply = "May I reply to it?"
            val fullReadSpeech = "$readText. $askReply"

            view?.update(PillState.SPEAKING, "Reading notification…", "$readText\n\n$askReply")
            view?.updateLiveCaption("$readText\n\n$askReply")
            voice.speakAndAwait(fullReadSpeech)

            // Listen for user response (Yes / No to reply)
            view?.update(PillState.LISTENING, "May I reply to it?", "")
            val resp2 = voice.listen(timeoutMs = 12000, force = true).getOrNull()?.trim()?.lowercase().orEmpty()

            val isYes2 = resp2.contains("yes") || resp2.contains("yeah") || resp2.contains("sure") ||
                resp2.contains("reply") || resp2.contains("send") || resp2.contains("ok") ||
                resp2.contains("okay") || resp2.contains("yup") || resp2.contains("please")

            if (!isYes2) {
                val keepingMsg = "Okay, keeping it unreplied."
                view?.update(PillState.SPEAKING, "AURA", "Keeping unreplied")
                view?.updateLiveCaption("Keeping unreplied")
                voice.speakAndAwait(keepingMsg)
                delay(1200)
                if (!runtime.isRunning) stopSelf()
                return@launch
            }

            // Step 3: Ask what to reply
            val askWhatMsg = "What would you like to reply?"
            view?.update(PillState.SPEAKING, "AURA", askWhatMsg)
            view?.updateLiveCaption(askWhatMsg)
            voice.speakAndAwait(askWhatMsg)

            // Listen for user's reply message
            view?.update(PillState.LISTENING, "Listening for your reply…", "")
            val replyContent = voice.listen(timeoutMs = 20000, force = true).getOrNull()?.trim().orEmpty()

            if (replyContent.isBlank()) {
                val timeoutMsg = "Didn't catch any reply, boss."
                view?.update(PillState.SPEAKING, "AURA", timeoutMsg)
                view?.updateLiveCaption(timeoutMsg)
                voice.speakAndAwait(timeoutMsg)
                delay(1200)
                if (!runtime.isRunning) stopSelf()
                return@launch
            }

            // Step 4: Send the reply
            view?.update(PillState.IDLE, "Sending…", "Sending to $sender:\n\"$replyContent\"")
            view?.updateLiveCaption("Sending: \"$replyContent\"…")

            val actionToUse = replyActionTitle ?: "Reply"
            val listener = AuraNotificationListenerService.current
            val sendError = listener?.fireAction(key, actionToUse, replyContent)
            if (sendError != null) {
                Log.w(TAG, "Direct reply error: $sendError; trying fallback act")
                AuraNotificationListenerService.act(this@AuraOverlayService, key, actionToUse)
            }

            val sentConfirm = "Message sent to $sender."
            view?.update(PillState.SPEAKING, "Sent", "[✓] Message sent to $sender:\n\"$replyContent\"")
            view?.updateLiveCaption("[✓] Message sent to $sender:\n\"$replyContent\"")
            voice.speakAndAwait(sentConfirm)

            delay(2800)
            if (!runtime.isRunning) stopSelf()
        }
    }

    private fun pillState(): PillState = when {
        voice.listening.value -> PillState.LISTENING
        voice.speaking.value -> PillState.SPEAKING
        else -> PillState.IDLE
    }

    private var observed = false
    private fun observeAll() {
        if (observed) return
        observed = true

        // 1. Observe voice transcripts (live StT)
        scope.launch {
            voice.transcript.collect { transcript ->
                if (notificationJob?.isActive == true && transcript.isNotBlank()) {
                    view?.update(
                        state = pillState(),
                        status = "Listening…",
                        transcript = transcript,
                        amplitude = 0.5f,
                    )
                    return@collect
                }
                view?.update(
                    state = pillState(),
                    status = when (pillState()) {
                        PillState.LISTENING -> "Listening…"
                        PillState.SPEAKING -> "Speaking…"
                        PillState.IDLE -> if (runtime.isRunning) "Working…" else "Tap to talk"
                    },
                    transcript = transcript,
                    amplitude = if (voice.listening.value) 0.5f else 0.1f,
                )
            }
        }

        // 2. Observe voice listening state
        scope.launch {
            voice.listening.collect { listening ->
                if (notificationJob?.isActive == true) return@collect
                view?.update(
                    state = pillState(),
                    status = if (listening) "Listening…" else if (runtime.isRunning) "Working…" else "Tap to talk",
                    transcript = voice.transcript.value,
                    amplitude = if (listening) 0.6f else 0f,
                )
            }
        }

        // 3. Observe voice speaking state
        scope.launch {
            voice.speaking.collect { speaking ->
                if (notificationJob?.isActive == true) return@collect
                view?.update(
                    state = pillState(),
                    status = if (speaking) "Speaking…" else if (runtime.isRunning) "Working…" else "Tap to talk",
                    transcript = voice.transcript.value,
                    amplitude = if (speaking) 0.7f else 0f,
                )
            }
        }

        // 4. Observe live RMS audio amplitude
        scope.launch {
            voice.rmsDb.collect { rms ->
                if (voice.listening.value) {
                    val normalized = ((rms + 2f) / 12f).coerceIn(0f, 1f)
                    view?.update(
                        state = pillState(),
                        status = "Listening…",
                        transcript = voice.transcript.value,
                        amplitude = normalized,
                    )
                }
            }
        }

        // 5. Observe StatusNarrator for live caption updates
        scope.launch {
            StatusNarrator.detail.collect { detail ->
                if (!detail.isNullOrBlank() && notificationJob?.isActive != true) {
                    view?.updateLiveCaption(detail)
                }
            }
        }

        // 6. Observe AgentStatusRegistry for tool narration / beat
        scope.launch {
            AgentStatusRegistry.line.collect { line ->
                if (line.isNotBlank() && notificationJob?.isActive != true) {
                    view?.updateLiveCaption(line)
                }
            }
        }
        scope.launch {
            AgentStatusRegistry.beat.collect { beat ->
                if (beat != null && beat.text.isNotBlank() && notificationJob?.isActive != true) {
                    view?.updateLiveCaption(beat.text)
                }
            }
        }

        // 7. Observe AgentRuntime state for task progress & todo-list
        scope.launch {
            runtime.state.collectLatest { state ->
                if (notificationJob?.isActive == true) return@collectLatest
                val goal = state.goal.ifBlank { currentGoal }
                if (state.running) {
                    view?.setTaskData(goal, isRunning = true)
                    if (state.planSteps.isNotEmpty()) {
                        val formatted = state.planSteps.map { s ->
                            val mark = when (s.typedStatus) {
                                PlanStepStatus.DONE -> "[✓]"
                                PlanStepStatus.IN_PROGRESS -> "[•]"
                                PlanStepStatus.BLOCKED -> "[✗]"
                                PlanStepStatus.SKIPPED -> "[-]"
                                else -> "[ ]"
                            }
                            "$mark ${s.description}"
                        }
                        view?.updateTodoList(formatted)
                    }
                } else if (state.outcome != null) {
                    val answer = state.answer ?: "Task finished"
                    view?.setCompletion(answer)
                    mainHandler.postDelayed({
                        if (!runtime.isRunning && notificationJob?.isActive != true) {
                            stopSelf()
                        }
                    }, 4000)
                }
            }
        }

        // 8. Observe HITL AskUserBroker questions
        scope.launch {
            AskUserBroker.shared.pending.collect { q ->
                if (notificationJob?.isActive != true) {
                    view?.setPendingQuestion(q?.question)
                }
            }
        }
    }

    override fun onDestroy() {
        instance = null
        observed = false
        notificationJob?.cancel()
        scope.cancel()
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AuraOverlayService"

        const val ACTION_RUN_TASK = "com.aura.aura_ui.overlay.RUN_TASK"
        const val EXTRA_TASK_TEXT = "extra_task_text"
        const val ACTION_SHOW = "com.aura.aura_ui.overlay.SHOW"
        const val ACTION_SHOW_AND_LISTEN = "com.aura.aura_ui.overlay.SHOW_AND_LISTEN"
        const val ACTION_SHOW_TASK = "com.aura.aura_ui.overlay.SHOW_TASK"
        const val ACTION_HIDE = "com.aura.aura_ui.overlay.HIDE"
        const val ACTION_NOTIFICATION_INTERACTION = "com.aura.aura_ui.overlay.NOTIFICATION_INTERACTION"

        const val EXTRA_TASK_GOAL = "extra_task_goal"
        const val EXTRA_NOTIF_KEY = "extra_notif_key"
        const val EXTRA_NOTIF_APP = "extra_notif_app"
        const val EXTRA_NOTIF_SENDER = "extra_notif_sender"
        const val EXTRA_NOTIF_TEXT = "extra_notif_text"
        const val EXTRA_NOTIF_REPLY_ACTION = "extra_notif_reply_action"

        @Volatile
        private var instance: AuraOverlayService? = null
        fun getInstance(): AuraOverlayService? = instance

        fun start(context: Context) {
            show(context)
        }

        fun show(context: Context, source: String = "app") {
            val intent = Intent(context, AuraOverlayService::class.java).setAction(ACTION_SHOW)
            context.startForegroundService(intent)
        }

        fun showTask(context: Context, goal: String) {
            val intent = Intent(context, AuraOverlayService::class.java)
                .setAction(ACTION_SHOW_TASK)
                .putExtra(EXTRA_TASK_GOAL, goal)
            context.startForegroundService(intent)
        }

        fun showAndListen(context: Context, source: String = "app") {
            val intent = Intent(context, AuraOverlayService::class.java).setAction(ACTION_SHOW_AND_LISTEN)
            context.startForegroundService(intent)
        }

        fun startNotificationFlow(
            context: Context,
            key: String,
            appName: String,
            sender: String,
            text: String,
            replyAction: String?,
        ) {
            val intent = Intent(context, AuraOverlayService::class.java)
                .setAction(ACTION_NOTIFICATION_INTERACTION)
                .putExtra(EXTRA_NOTIF_KEY, key)
                .putExtra(EXTRA_NOTIF_APP, appName)
                .putExtra(EXTRA_NOTIF_SENDER, sender)
                .putExtra(EXTRA_NOTIF_TEXT, text)
                .putExtra(EXTRA_NOTIF_REPLY_ACTION, replyAction)
            context.startForegroundService(intent)
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, AuraOverlayService::class.java))
        }

        fun hideIfRunning() {
            instance?.stopSelf()
        }

        fun minimize(context: Context) {
            // Keep overlay displayed over other apps during task execution
        }

        fun restore(context: Context) {
            show(context)
        }

        fun updateLiveNotification(agent: String, output: String) {
            instance?.view?.updateLiveCaption(output)
        }

        fun updateLiveNotificationProgress(agent: String, step: String) {
            instance?.view?.updateLiveCaption(step)
        }

        fun updateLiveNotificationProgress(
            goal: String,
            current: Int,
            total: Int,
            isComplete: Boolean = false,
            isAborted: Boolean = false,
        ) {
            val status = if (isComplete) "Completed" else if (isAborted) "Stopped" else " of "
            instance?.view?.updateLiveCaption(" ()")
        }

        fun updateLiveNotificationProgress(
            goal: String,
            current: Int,
            total: Int,
            isComplete: Boolean = false,
            isAborted: Boolean = false,
        ) {
            val status = if (isComplete) "Completed" else if (isAborted) "Stopped" else " of "
            instance?.view?.updateLiveCaption(" ()")
        }

        fun endVoiceConversation() {
            instance?.stopSelf()
        }
    }
}
