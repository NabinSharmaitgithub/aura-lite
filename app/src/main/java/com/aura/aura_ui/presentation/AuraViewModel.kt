package com.aura.aura_ui.presentation

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.aura_ui.accessibility.AuraAccessibilityService
import com.aura.aura_ui.accessibility.ScreenCaptureManager
import com.aura.aura_ui.agent.AgentRuntime
import com.aura.aura_ui.agent.control.ControlLock
import com.aura.aura_ui.agent.report.RunEntryMode
import com.aura.aura_ui.agent.ConfirmationBroker
import com.aura.aura_ui.agent.llm.LlmEndpoint
import com.aura.aura_ui.agent.llm.LlmEndpointCatalog
import com.aura.aura_ui.agent.llm.ModelCatalog
import com.aura.aura_ui.agent.llm.ModelInfo
import com.aura.aura_ui.agent.llm.ProviderKeyStore
import com.aura.aura_ui.agent.research.WebSearchClient
import com.aura.aura_ui.automation.AppEntry
import com.aura.aura_ui.automation.AppResolver
import com.aura.aura_ui.data.db.ReminderDao
import com.aura.aura_ui.data.db.ReminderEntity
import com.aura.aura_ui.data.eval.EvalCatalog
import com.aura.aura_ui.data.eval.EvalTask
import com.aura.aura_ui.data.eval.EvalVerdict
import com.aura.aura_ui.data.eval.EvalVerdictRecord
import com.aura.aura_ui.data.eval.EvalVerdictRecorder
import com.aura.aura_ui.state.EvalRunState
import com.aura.aura_ui.data.learnings.LearningsStore
import com.aura.aura_ui.data.learnings.LearningFact
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.data.prefs.RestrictedAppsRepository
import com.aura.aura_ui.data.prefs.SecureKeyStore
import com.aura.aura_ui.data.prefs.SettingsSnapshot
import com.aura.aura_ui.data.session.SessionLogStore
import com.aura.aura_ui.data.session.SessionOutcome
import com.aura.aura_ui.data.session.SessionSummary
import com.aura.aura_ui.data.session.TraceEvent
import com.aura.aura_ui.data.skills.Skill
import com.aura.aura_ui.data.skills.SkillsRepository
import com.aura.aura_ui.services.MediaProjectionService
import com.aura.aura_ui.overlay.AuraOverlayService
import com.aura.aura_ui.services.AssistantForegroundService
import com.aura.aura_ui.services.WakeWordListeningService
import com.aura.aura_ui.voice.VoiceEngine
import com.aura.aura_ui.voice.WakeWordEngine
import com.aura.mcp.McpServerController
import com.aura.mcp.McpServerHealth
import com.aura.mcp.lan.PairingPayload
import com.aura.mcp.lan.TrustedDevice
import com.aura.mcp.tools.ToolCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.aura.aura_ui.firebase.FirebaseAuthManager
import com.aura.aura_ui.firebase.FirebaseDiagnostics
import com.aura.aura_ui.firebase.FirebaseOptional
import com.aura.aura_ui.firebase.FirebaseSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionSnapshot(
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val notificationListener: Boolean = false,
    val notifications: Boolean = false,
    val microphone: Boolean = false,
    val contacts: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val exactAlarms: Boolean = false,
    val screenCapture: Boolean = false,
)

data class KeyVerification(
    val ok: Boolean,
    val message: String,
    val modelCount: Int = 0,
)

data class ReportStats(
    val runs: Int,
    val successRate: Float,
    val avgSteps: Float,
    val avgDurationMs: Long,
    val topProvider: String,
)

/**
 * Snapshot for the eval debug cockpit: live run state, control-lock diagnostics, structured
 * report and recorded verdicts, decoupled from model prose.
 */
data class EvalCockpit(
    val status: String,
    val outcome: String?,
    val termination: String?,
    val entryMode: String,
    val steps: Int,
    val goal: String,
    val paused: Boolean,
    val controlLockPaused: Boolean,
    val controlLockHolding: Boolean,
    val controlLockReason: String?,
    val verdicts: List<EvalVerdictRecord>,
    val reportMarkdown: String?,
)

/**
 * One shared view model for the whole shell. Screens read the same settings revision, agent
 * state and session list so a run started on Home keeps updating while the user is in Settings.
 */
@HiltViewModel
class AuraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AuraSettings,
    private val runtime: AgentRuntime,
    private val confirmations: ConfirmationBroker,
    private val sessions: SessionLogStore,
    private val keyStore: ProviderKeyStore,
    private val modelCatalog: ModelCatalog,
    private val skillsRepository: SkillsRepository,
    private val learnings: LearningsStore,
    private val reminderDao: ReminderDao,
    private val reminderScheduler: com.aura.aura_ui.agent.memory.ReminderScheduler,
    private val mcp: McpServerController,
    private val restricted: RestrictedAppsRepository,
    private val voice: VoiceEngine,
    private val capture: ScreenCaptureManager,
    private val appResolver: AppResolver,
    private val search: WebSearchClient,
    private val firebaseAuth: FirebaseAuthManager,
    private val firebaseSync: FirebaseSync,
    private val diagnostics: FirebaseDiagnostics,
    private val b2: com.aura.aura_ui.b2.B2Service,
    private val secureKeys: SecureKeyStore,
    private val askUsers: com.aura.aura_ui.agent.AskUserBroker,
    private val evalRecorder: EvalVerdictRecorder,
) : ViewModel() {

    val settings: StateFlow<SettingsSnapshot> = prefs.revision
        .map { prefs.snapshot() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, prefs.snapshot())

    val agent = runtime.state
    val confirmation = confirmations.pending

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val recentSessions: StateFlow<List<SessionSummary>> = _sessions.asStateFlow()

    /** The interrupted goal from the last run that ended abruptly, if any. */
    val interruptedGoal: StateFlow<String?> = MutableStateFlow(sessions.readInterruption()?.second).asStateFlow()

    /** Resume the goal that was interrupted, clearing the marker. */
    fun resumeInterruptedRun() {
        val goal = interruptedGoal.value
        if (goal != null) {
            sessions.clearInterruption()
            submit(goal, RunEntryMode.RESUMED)
            return
        }
        // Fall back to an atomic run checkpoint (pause/failure/budget) when no legacy
        // interruption marker exists.
        runtime.resumeLatestCheckpoint()
    }

    /** Dismiss the interrupted-run banner. */
    fun dismissInterruptedRun() {
        sessions.clearInterruption()
        (interruptedGoal as MutableStateFlow).value = null
    }

    val reportStats: StateFlow<ReportStats> = _sessions.map { list ->
        val finished = list.filter { it.endedAt != null }
        val success = finished.count { it.outcome == SessionOutcome.SUCCESS }
        ReportStats(
            runs = list.size,
            successRate = if (finished.isEmpty()) 0f else success.toFloat() / finished.size,
            avgSteps = if (finished.isEmpty()) 0f else finished.map { it.steps }.average().toFloat(),
            avgDurationMs = if (finished.isEmpty()) 0L else finished.map { it.durationMs }.average().toLong(),
            topProvider = run {
                val counts = HashMap<String, Int>()
                finished.forEach { counts[it.provider.ifBlank { "—" }] = (counts[it.provider.ifBlank { "—" }] ?: 0) + 1 }
                counts.maxByOrNull { it.value }?.key ?: "—"
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportStats(0, 0f, 0f, 0L, "—"))

    val skillList: StateFlow<List<Skill>> = skillsRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _facts = MutableStateFlow<List<LearningFact>>(emptyList())
    val facts: StateFlow<List<LearningFact>> = _facts.asStateFlow()

    val reminders: StateFlow<List<ReminderEntity>> = reminderDao.observePending()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun firebaseSignIn(email: String, password: String) = viewModelScope.launch {
        if (_firebaseBusy.value) return@launch
        _firebaseBusy.value = true
        val result = withContext(Dispatchers.IO) { firebaseAuth.signInWithEmail(email, password) }
        _firebaseBusy.value = false
        result.onSuccess { user ->
            prefs.firebaseEnabled = true
            _firebaseEmail.value = user.email
            _firebaseMessage.value = "Linked ${user.email ?: "your account"}. Sync is ready."
        }.onFailure { _firebaseMessage.value = "Sign-in failed: ${it.message}" }
    }

    fun firebaseCreateAccount(email: String, password: String) = viewModelScope.launch {
        if (_firebaseBusy.value) return@launch
        _firebaseBusy.value = true
        val result = withContext(Dispatchers.IO) { firebaseAuth.createAccountWithEmail(email, password) }
        _firebaseBusy.value = false
        result.onSuccess { user ->
            prefs.firebaseEnabled = true
            _firebaseEmail.value = user.email
            _firebaseMessage.value = "Account created. Sync is ready."
        }.onFailure { _firebaseMessage.value = "Account creation failed: ${it.message}" }
    }

    fun firebaseSignInWithGoogle(idToken: String) = viewModelScope.launch {
        if (_firebaseBusy.value) return@launch
        _firebaseBusy.value = true
        val result = withContext(Dispatchers.IO) { firebaseAuth.signInWithGoogle(idToken) }
        _firebaseBusy.value = false
        result.onSuccess { user ->
            prefs.firebaseEnabled = true
            _firebaseEmail.value = user.email
            _firebaseMessage.value = "Linked Google account. Sync is ready."
        }.onFailure { _firebaseMessage.value = "Google sign-in failed: ${it.message}" }
    }

    fun firebaseSignOut() {
        firebaseAuth.signOut()
        prefs.firebaseEnabled = false
        _firebaseEmail.value = null
        _firebaseMessage.value = "Signed out. AURA is fully offline again."
    }

    fun firebaseSyncNow() = viewModelScope.launch {
        if (_firebaseBusy.value) return@launch
        _firebaseBusy.value = true
        val report = withContext(Dispatchers.IO) { firebaseSync.syncNow() }
        _firebaseBusy.value = false
        _firebaseMessage.value = report.detail
        diagnostics.logSync(report.pushed.size, report.pulled.size, report.skipped.size + report.errors.size)
    }


    private val _b2Message = MutableStateFlow("")
    val b2Message: StateFlow<String> = _b2Message.asStateFlow()

    val b2Configured: Boolean get() = b2.isConfigured()
    val b2MaskedKey: String? get() = secureKeys.maskedB2ApplicationKey()

    fun saveB2Credentials(bucket: String, endpoint: String, keyId: String, applicationKey: String) = viewModelScope.launch {
        _b2Message.value = "Saving…"
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                prefs.b2BucketName = bucket.trim()
                prefs.b2Endpoint = endpoint.trim()
                if (keyId.isNotBlank()) secureKeys.b2KeyId = keyId
                if (applicationKey.isNotBlank()) secureKeys.b2ApplicationKey = applicationKey
                true
            }.getOrDefault(false)
        }
        if (!saved) {
            _b2Message.value = "Could not save the B2 settings"
            return@launch
        }
        val result = withContext(Dispatchers.IO) { b2.testConnection() }
        result.fold(
            onSuccess = { report -> _b2Message.value = "Connected · " + report },
            onFailure = { error -> _b2Message.value = "Saved, but the bucket is unreachable: " + (error.message ?: "unknown") },
        )
    }

    fun testB2Connection() = viewModelScope.launch {
        _b2Message.value = "Testing…"
        withContext(Dispatchers.IO) { b2.testConnection() }.fold(
            onSuccess = { report -> _b2Message.value = report },
            onFailure = { error -> _b2Message.value = error.message ?: "Unreachable" },
        )
    }

    fun clearB2Credentials() {
        secureKeys.b2KeyId = null
        secureKeys.b2ApplicationKey = null
        prefs.apply {
            b2BucketName = ""
            b2Endpoint = ""
            b2SessionBackupEnabled = false
        }
        _b2Message.value = "Cleared. Nothing is uploaded until you configure B2 again."
    }

    fun setB2SessionBackup(enabled: Boolean) = updateSettings { it.b2SessionBackupEnabled = enabled }

    fun exportSessionToB2(sessionId: String) = viewModelScope.launch {
        _b2Message.value = "Exporting…"
        val result = withContext(Dispatchers.IO) { b2.exportSession(sessionId) }
        result.fold(
            onSuccess = { key -> _b2Message.value = "Exported to " + key },
            onFailure = { error -> _b2Message.value = "Export failed: " + (error.message ?: "unknown") },
        )
    }


    val askPending: StateFlow<com.aura.aura_ui.agent.AskUserBroker.Pending?> = askUsers.pending

    fun answerAsk(answer: String) = askUsers.answer(answer)

    fun dismissAsk() = askUsers.dismiss()

    fun setFirebaseToggle(kind: String, enabled: Boolean) {
        prefs.apply {
            when (kind) {
                "analytics" -> firebaseAnalyticsEnabled = enabled
                "crashlytics" -> firebaseCrashlyticsEnabled = enabled
                "sessions" -> firebaseSessionSyncEnabled = enabled
                else -> Unit
            }
        }
        if (kind == "crashlytics") diagnostics.applyCrashToggle()
    }

    private val _mcpHealth = MutableStateFlow(McpServerHealth())
    val mcpHealth: StateFlow<McpServerHealth> = _mcpHealth.asStateFlow()

    private val _trusted = MutableStateFlow<List<TrustedDevice>>(emptyList())
    val trustedDevices: StateFlow<List<TrustedDevice>> = _trusted.asStateFlow()

    private val _audit = MutableStateFlow<List<String>>(emptyList())
    val auditLines: StateFlow<List<String>> = _audit.asStateFlow()

    private val _firebaseBusy = MutableStateFlow(false)
    val firebaseBusy: StateFlow<Boolean> = _firebaseBusy.asStateFlow()

    private val _firebaseMessage = MutableStateFlow("")
    val firebaseMessage: StateFlow<String> = _firebaseMessage.asStateFlow()

    private val _firebaseEmail = MutableStateFlow(firebaseAuth.email)
    val firebaseEmail: StateFlow<String?> = _firebaseEmail.asStateFlow()

    val firebaseAvailable: Boolean get() = FirebaseOptional.available(context)

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    private val _verification = MutableStateFlow<KeyVerification?>(null)
    val verification: StateFlow<KeyVerification?> = _verification.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _tavilyState = MutableStateFlow<KeyVerification?>(null)
    val tavilyState: StateFlow<KeyVerification?> = _tavilyState.asStateFlow()

    private val _restrictedPackages = MutableStateFlow<Set<String>>(restricted.blockedPackages())
    val restrictedPackages: StateFlow<Set<String>> = _restrictedPackages.asStateFlow()

    private val evalCatalog = EvalCatalog(context)

    private val _evalRun = MutableStateFlow<EvalRunState>(EvalRunState.Idle)
    /** Explicit eval-run lifecycle for the debug cockpit. */
    val evalRun: StateFlow<EvalRunState> = _evalRun.asStateFlow()

    val voiceListening = voice.listening
    val voiceSpeaking = voice.speaking
    val voiceTranscript = voice.transcript
    val consentRequested = capture.consentRequested

    val endpoints: List<LlmEndpoint> get() = LlmEndpointCatalog.all
    val toolCount: Int get() = ToolCatalog.all.size
    val tools get() = ToolCatalog.all
    val wakeWordAvailable: Boolean get() = WakeWordEngine.modelsPresent(context)
    val speechAvailable: Boolean get() = voice.isAvailable

    init {
        viewModelScope.launch {
            skillsRepository.ensureSeeded()
            _facts.value = learnings.facts()
            refreshSessions()
            refreshTrusted()
            _mcpHealth.value = mcp.health.value
        }
        viewModelScope.launch {
            mcp.health.collect { health -> _mcpHealth.value = health }
        }
        viewModelScope.launch {
            restricted.revision.collect { _restrictedPackages.value = restricted.blockedPackages() }
        }
    }

    // ── Settings ────────────────────────────────────────────────────────────────

    fun updateSettings(block: (AuraSettings) -> Unit) {
        val before = prefs.snapshot().ttsVoice
        block(prefs)
        // A saved TTS voice/engine change must take effect on the live engine immediately,
        // not only on the next `speak()` call.
        if (prefs.ttsVoice != before) {
            runCatching { voice.refreshTtsVoice() }
        }
    }

    fun setOnboardingStep(step: Int) {
        prefs.onboardingStep = step
    }

    fun completeOnboarding() {
        prefs.onboardingComplete = true
    }

    fun resetOnboarding() {
        prefs.resetOnboarding()
    }

    // ── Agent ────────────────────────────────────────────────────────────────────

    fun submit(goal: String, entryMode: RunEntryMode = RunEntryMode.FRESH) {
        if (goal.isBlank()) return
        AssistantForegroundService.submit(context, goal.trim(), entryMode)
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            refreshSessions()
        }
    }

    fun startListening() {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            showNotice("Microphone permission is off. Open Settings → Permissions and grant it.")
            return
        }
        if (!voice.isAvailable) {
            showNotice("Speech recognition is not available on this device.")
            return
        }
        AssistantForegroundService.listen(context)
    }

    fun pauseRun() = runtime.pause()

    fun resumeRun() = runtime.resume()

    fun stopRun() {
        runtime.stop()
        viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            refreshSessions()
        }
    }

    fun dismissRun() {
        runtime.dismiss()
        refreshSessions()
    }

    fun approveConfirmation() {
        confirmation.value?.let { confirmations.respond(it.id, true) }
    }

    fun denyConfirmation() {
        confirmation.value?.let { confirmations.respond(it.id, false) }
    }

    // ── Providers ────────────────────────────────────────────────────────────────

    fun endpoint(id: String): LlmEndpoint = LlmEndpointCatalog.byId(id)

    fun currentEndpoint(): LlmEndpoint {
        val snapshot = settings.value
        return if (snapshot.llmEndpointId == "custom") {
            LlmEndpointCatalog.fromCustomJson(snapshot.customEndpointJson) ?: LlmEndpointCatalog.custom
        } else {
            LlmEndpointCatalog.byId(snapshot.llmEndpointId)
        }
    }

    fun selectEndpoint(id: String) {
        prefs.llmEndpointId = id
        prefs.llmModelId = ""
        _models.value = emptyList()
        _verification.value = null
        val endpoint = endpoint(id)
        if (keyStore.has(endpoint)) fetchModels()
    }

    fun selectModel(id: String) {
        prefs.llmModelId = id
        _notice.value = "Model set to $id"
    }

    fun saveProviderKey(endpoint: LlmEndpoint, key: String) {
        if (key.isBlank()) keyStore.remove(endpoint) else keyStore.save(endpoint, key)
        _notice.value = if (key.isBlank()) "Key removed" else "Key saved locally"
        _verification.value = null
        if (key.isNotBlank()) fetchModels()
    }

    fun verifyProviderKey(endpoint: LlmEndpoint, key: String) {
        viewModelScope.launch {
            val candidate = key.ifBlank { keyStore.keyFor(endpoint).orEmpty() }
            if (candidate.isBlank()) {
                _verification.value = KeyVerification(false, "Enter a key first")
                return@launch
            }
            _modelsLoading.value = true
            val result = modelCatalog.fetch(endpoint, candidate)
            _modelsLoading.value = false
            result.onSuccess { list ->
                _models.value = list
                _verification.value = KeyVerification(
                    ok = true,
                    message = if (list.isEmpty()) "Key accepted · no models returned" else "Key verified",
                    modelCount = list.size,
                )
                if (list.isNotEmpty() && settings.value.llmModelId.isBlank()) {
                    prefs.llmModelId = endpoint.defaultModelId
                        ?.takeIf { id -> list.any { it.id == id } }
                        ?: list.first().id
                }
            }.onFailure { error ->
                _verification.value = KeyVerification(false, error.message ?: "Verification failed")
            }
        }
    }

    fun fetchModels() {
        val endpoint = currentEndpoint()
        val key = keyStore.keyFor(endpoint).orEmpty()
        if (key.isBlank()) {
            _notice.value = "Add a key to load models"
            return
        }
        viewModelScope.launch {
            _modelsLoading.value = true
            modelCatalog.fetch(endpoint, key)
                .onSuccess { list ->
                    _models.value = list
                    if (_verification.value == null) {
                        _verification.value = KeyVerification(true, "Key verified", list.size)
                    }
                }
                .onFailure { error ->
                    _verification.value = KeyVerification(false, error.message ?: "Could not load models")
                }
            _modelsLoading.value = false
        }
    }

    fun keyHint(endpoint: LlmEndpoint): String? = keyStore.hint(endpoint)

    fun hasKey(endpoint: LlmEndpoint): Boolean = keyStore.has(endpoint)

    fun setTemperature(value: Float) {
        prefs.temperature = value
    }

    fun setMaxSteps(value: Int) {
        prefs.maxSteps = value
    }

    fun saveCustomEndpoint(raw: String) {
        val parsed = LlmEndpointCatalog.fromCustomJson(raw)
        if (parsed == null || parsed.baseUrl.isBlank()) {
            _notice.value = "Custom endpoint JSON needs at least baseUrl"
            return
        }
        prefs.customEndpointJson = raw.trim()
        prefs.llmEndpointId = "custom"
        _notice.value = "Custom endpoint saved"
        if (keyStore.has(parsed)) fetchModels()
    }

    fun saveSystemPromptOverride(text: String) {
        prefs.systemPromptOverride = text.trim()
        _notice.value = if (text.isBlank()) "Using the built-in system prompt" else "System prompt override saved"
    }

    // ── Skills / memory ──────────────────────────────────────────────────────────

    fun setSkillEnabled(skill: Skill, enabled: Boolean) {
        viewModelScope.launch { skillsRepository.setEnabled(skill.id, enabled) }
    }

    fun importSkill(raw: String) {
        viewModelScope.launch {
            _notice.value = if (skillsRepository.import(raw)) "Skill imported" else "That skill file could not be parsed"
        }
    }

    fun deleteSkill(skill: Skill) {
        viewModelScope.launch {
            skillsRepository.delete(skill.id)
            _notice.value = "Skill deleted"
        }
    }

    fun addFact(text: String, scope: String = "global") {
        if (text.isBlank()) return
        learnings.add(
            LearningFact(
                id = "l_" + System.currentTimeMillis().toString(36),
                scope = scope.ifBlank { "global" },
                text = text.trim(),
                confidence = 1f,
                sources = listOf("manual"),
            ),
        )
        _facts.value = learnings.facts()
        _notice.value = "Added to memory"
    }

    fun deleteFact(fact: LearningFact) {
        learnings.delete(fact.id)
        _facts.value = learnings.facts()
    }

    fun clearFacts(scope: String? = null) {
        learnings.clear(scope)
        _facts.value = learnings.facts()
    }

    fun addReminder(text: String, minutesFromNow: Int) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val at = System.currentTimeMillis() + minutesFromNow.coerceAtLeast(1) * 60_000L
            reminderScheduler.schedule(text.trim(), at)
            _notice.value = "Reminder set"
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            reminderScheduler.cancel(reminder.id)
            reminderDao.delete(reminder.id)
        }
    }

    fun corrections(): List<Pair<String, String>> = learnings.corrections()

    fun clearCorrections() {
        learnings.clearCorrections()
        _notice.value = "Voice corrections cleared"
    }

    // ── Sessions ─────────────────────────────────────────────────────────────────

    fun refreshSessions() {
        viewModelScope.launch {
            _sessions.value = withContext(Dispatchers.IO) { sessions.sessions(100) }
        }
    }

    fun sessionEvents(sessionId: String): List<TraceEvent> = sessions.events(sessionId)

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sessions.delete(sessionId) }
            refreshSessions()
        }
    }

    fun pruneSessions() {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { sessions.prune(settings.value.logRetentionDays) }
            _notice.value = "Pruned $removed session${if (removed == 1) "" else "s"}"
            refreshSessions()
        }
    }

    fun clearSessions() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                sessions.sessions(Int.MAX_VALUE).forEach { sessions.delete(it.id) }
            }
            _notice.value = "All session logs deleted"
            refreshSessions()
        }
    }

    fun usageBytes(): Long = sessions.usageBytes()

    fun relativeTime(t: Long): String = sessions.relativeTime(t)

    fun screenshotFor(sessionId: String, name: String) = sessions.screenshotFile(sessionId, name)

    // ── MCP ─────────────────────────────────────────────────────────────────────

    fun startMcpServer() {
        val port = settings.value.mcpPort
        val health = mcp.start(port)
        _mcpHealth.value = health
        prefs.mcpEnabled = health.running
        refreshTrusted()
        _audit.value = mcp.auditLines(80)
        if (!health.running) _notice.value = health.lastError ?: "Server could not start"
    }

    fun stopMcpServer() {
        mcp.stop()
        prefs.mcpEnabled = false
        _mcpHealth.value = mcp.health.value
    }

    fun setMcpPort(port: Int) {
        prefs.mcpPort = port
        if (mcp.isRunning) {
            mcp.stop()
            startMcpServer()
        }
    }

    fun beginPairing(): PairingPayload? {
        if (!mcp.isRunning) startMcpServer()
        val payload = mcp.pairingPayload()
        refreshTrusted()
        return payload
    }

    fun refreshTrusted() {
        _trusted.value = mcp.trustedDevices()
    }

    fun revokeDevice(clientId: String) {
        mcp.revoke(clientId)
        refreshTrusted()
    }

    fun refreshAudit() {
        _audit.value = mcp.auditLines(80)
    }

    // ── Restricted apps ─────────────────────────────────────────────────────────

    fun installedApps(): List<AppEntry> = appResolver.installedApps()

    fun setRestricted(packageName: String, blocked: Boolean) {
        restricted.setBlocked(packageName, blocked)
        _restrictedPackages.value = restricted.blockedPackages()
    }

    // ── Search key ──────────────────────────────────────────────────────────────

    fun saveTavilyKey(key: String) {
        keyStore.tavilyKey = key
        _tavilyState.value = null
        _notice.value = if (key.isBlank()) "Search key removed" else "Search key saved"
    }

    fun tavilyKeyHint(): String? = keyStore.tavilyKey?.let { key ->
        if (key.length <= 8) "••••" else key.take(4) + "••••" + key.takeLast(4)
    }

    fun verifyTavily() {
        viewModelScope.launch {
            val result = search.search("AURA connectivity check", 1)
            _tavilyState.value = if (result.ok) {
                KeyVerification(true, "Tavily key works")
            } else {
                KeyVerification(false, result.error?.message ?: "Tavily verification failed")
            }
        }
    }

    // ── Voice ────────────────────────────────────────────────────────────────────

    fun setWakeWord(enabled: Boolean): Boolean {
        if (enabled && !wakeWordAvailable) {
            prefs.wakeWordEnabled = false
            _notice.value = "Wake-word model is not bundled. Add assets/kws to enable it."
            return false
        }
        if (enabled && !permissions().microphone) {
            prefs.wakeWordEnabled = false
            _notice.value = "Microphone permission is required for the wake word."
            return false
        }
        prefs.wakeWordEnabled = enabled
        if (enabled) WakeWordListeningService.start(context) else WakeWordListeningService.stop(context)
        return true
    }

    fun setCompanionEnabled(enabled: Boolean) {
        prefs.companionEnabled = enabled
        val intent = Intent(context, AuraOverlayService::class.java)
        if (enabled) {
            if (Settings.canDrawOverlays(context)) {
                runCatching { context.startForegroundService(intent) }
            } else {
                prefs.companionEnabled = false
                _notice.value = "Allow display over other apps to use the companion overlay."
            }
        } else {
            runCatching { context.stopService(intent) }
        }
    }

    fun speak(text: String) {
        if (text.isNotBlank()) voice.speak(text)
    }

    fun ttsVoices(): List<String> = voice.voices()

    fun stopSpeaking() {
        voice.stopSpeaking()
    }

    fun requestScreenCapture() {
        // Android 14+ requires a running foreground service of type `mediaProjection`
        // before consent is requested and before MediaProjection is created.
        MediaProjectionService.start(context)
        capture.requestConsent()
    }

    fun consentIntent(): Intent = capture.createConsentIntent()

    fun handleConsentResult(resultCode: Int, data: Intent?) {
        capture.handleConsentResult(resultCode, data)
        prefs.screenCaptureEnabled = capture.hasConsent()
        if (!prefs.screenCaptureEnabled) {
            MediaProjectionService.stop(context)
        }
    }

    // ── Permissions / environment ────────────────────────────────────────────────

    fun permissions(): PermissionSnapshot {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return PermissionSnapshot(
            accessibility = AuraAccessibilityService.isEnabled(context),
            overlay = Settings.canDrawOverlays(context),
            notificationListener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
            notifications = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            microphone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            contacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
            exactAlarms = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms(),
            screenCapture = capture.hasConsent(),
        )
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun packageVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("dev")

    // ── Eval / notices ─────────────────────────────────────────────────────────

    fun evalTasks(): List<EvalTask> = evalCatalog.tasks()

    fun validateEval(tasks: List<EvalTask>): List<String> {
        val known = ToolCatalog.byName.keys
        return tasks.flatMap { task ->
            task.expectedTools.filterNot { it in known }.map { "${task.id}: unknown tool `$it`" }
        }
    }

    fun runEvalTask(task: EvalTask) {
        _evalRun.value = EvalRunState.Submitted(task.id, task.goal)
        submit(task.goal)
        viewModelScope.launch {
            val goal = task.goal.trim()
            val started = kotlinx.coroutines.withTimeoutOrNull(20_000) {
                runtime.state.first { it.running && it.goal == goal }
            }
            if (started == null) {
                _evalRun.value = EvalRunState.Failed(task.id, "run did not start within 20s")
                return@launch
            }
            _evalRun.value = EvalRunState.Running(task.id)
            val finished = kotlinx.coroutines.withTimeoutOrNull(15 * 60_000) {
                runtime.state.first { !it.running && it.outcome != null }
            } ?: run {
                _evalRun.value = EvalRunState.Failed(task.id, "run did not finish within 15m")
                return@launch
            }
            val ok = finished.outcome == SessionOutcome.SUCCESS
            val refused = finished.outcome == SessionOutcome.PERMISSION_DENIED ||
                finished.outcome == SessionOutcome.REMOTE_BLOCKED
            val note = task.note.orEmpty().lowercase()
            val expectedRefusal = note.contains("blocked") || note.contains("refuse") || note.contains("denied")
            val verdict = EvalVerdict.classify(
                ok = ok,
                refused = refused,
                expectedRefusal = expectedRefusal,
                completed = finished.outcome != null,
            )
            evalRecorder.append(
                EvalVerdictRecord(
                    taskId = task.id,
                    verdict = verdict,
                    steps = finished.stepCount,
                    detail = finished.report?.termination?.wire ?: finished.outcome,
                ),
            )
            _evalRun.value = EvalRunState.Finished(task.id, verdict.name, finished.stepCount)
        }
    }

    /** Recorded eval verdicts for the debug cockpit/harness. */
    fun evalVerdicts(): List<EvalVerdictRecord> = evalRecorder.readAll()

    /** Live debug-cockpit snapshot (status, control lock, structured report, verdicts). */
    fun evalCockpit(): EvalCockpit {
        val state = runtime.state.value
        val lock = ControlLock.diagnostics()
        return EvalCockpit(
            status = state.displayStatus,
            outcome = state.outcome,
            termination = state.termination?.wire ?: state.report?.termination?.wire,
            entryMode = state.entryMode.name.lowercase(),
            steps = state.stepCount,
            goal = state.goal,
            paused = state.paused,
            controlLockPaused = lock.paused,
            controlLockHolding = lock.holding,
            controlLockReason = lock.lastReason,
            verdicts = evalVerdicts(),
            reportMarkdown = state.report?.markdown(),
        )
    }

    fun showNotice(message: String) {
        _notice.value = message
    }

    fun clearNotice() {
        _notice.value = null
    }
}
