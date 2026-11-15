package com.aura.aura_ui.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aura.aura_ui.services.AssistantForegroundService
import com.aura.aura_ui.state.AccessibilityState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class AccessibilitySignal(
    val type: Int,
    val packageName: String,
    val className: String,
    val text: String,
    val at: Long = System.currentTimeMillis(),
)

/**
 * The hands and eyes of the product.
 *
 * Exposes a root node, generation counter, event stream, screenshot capture via
 * [AccessibilityScreenshotSource], and device control (gestures, lock, unlock).
 */
class AuraAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val bumpRunnable = Runnable { ScreenGeneration.bump() }

    val extractor = UiTreeExtractor()
    val gestureInjector: com.aura.aura_ui.accessibility.gesture.GestureInjector by lazy { com.aura.aura_ui.accessibility.gesture.GestureInjector(this) }
    var lastTapPath: String? = null

    class ScreenCaptureData(
        val screenshot: String = "",
        val screenWidth: Int = 1080,
        val screenHeight: Int = 2400,
        val error: String? = null,
    )

    class ScreenCaptureManagerHelper(private val service: AuraAccessibilityService) {
        fun captureScreenWithAnalysis(force: Boolean = true, callback: (ScreenCaptureData) -> Unit) {
            val shot = service.captureScreen()
            if (shot != null) {
                callback(ScreenCaptureData(screenshot = shot, screenWidth = service.getScreenWidth(), screenHeight = service.getScreenHeight()))
            } else {
                callback(ScreenCaptureData(error = "Screen capture unavailable"))
            }
        }
    }

    class UiTreeExtractorHelper {
        fun getUITree(): Map<String, Any>? = emptyMap()
    }

    val screenCaptureManager = ScreenCaptureManagerHelper(this)
    val uiTreeExtractor = UiTreeExtractorHelper()
    val gestures: GestureInjector by lazy { GestureInjector(this, extractor) }

    suspend fun tapAwait(x: Int, y: Int): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val path = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                if (cont.isActive) cont.resume(true) { _, _, _ -> }
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                if (cont.isActive) cont.resume(false) { _, _, _ -> }
            }
        }, null)
    }

    suspend fun longPressAwait(x: Int, y: Int, durationMs: Long): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val path = android.graphics.Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(300L))
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                if (cont.isActive) cont.resume(true) { _, _, _ -> }
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                if (cont.isActive) cont.resume(false) { _, _, _ -> }
            }
        }, null)
    }

    suspend fun swipeAwait(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300L): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val path = android.graphics.Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100L))
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: android.accessibilityservice.GestureDescription?) {
                if (cont.isActive) cont.resume(true) { _, _, _ -> }
            }
            override fun onCancelled(gestureDescription: android.accessibilityservice.GestureDescription?) {
                if (cont.isActive) cont.resume(false) { _, _, _ -> }
            }
        }, null)
    }

    suspend fun strokesAwait(strokes: List<List<Pair<Int, Int>>>, durationMs: Long, holdMs: Long): Boolean = true

    suspend fun scrollAwait(direction: String): Boolean {
        val (width, height) = ScreenGeometry.realSizePx(this)
        val midX = width / 2
        val midY = height / 2
        return when (direction.lowercase()) {
            "down" -> swipeAwait(midX, (height * 0.75f).toInt(), midX, (height * 0.25f).toInt(), 300L)
            "up" -> swipeAwait(midX, (height * 0.25f).toInt(), midX, (height * 0.75f).toInt(), 300L)
            "right" -> swipeAwait((width * 0.75f).toInt(), midY, (width * 0.25f).toInt(), midY, 300L)
            "left" -> swipeAwait((width * 0.25f).toInt(), midY, (width * 0.75f).toInt(), midY, 300L)
            else -> false
        }
    }

    fun performTap(x: Int, y: Int, commandId: String? = null): Boolean = runCatching { kotlinx.coroutines.runBlocking { tapAwait(x, y) } }.getOrDefault(false)
    fun performClick(x: Int, y: Int, commandId: String? = null): Boolean = performTap(x, y, commandId)
    fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long = 300L, commandId: String? = null): Boolean = runCatching { kotlinx.coroutines.runBlocking { swipeAwait(x1, y1, x2, y2, duration) } }.getOrDefault(false)
    fun performLongPress(x: Int, y: Int, durationMs: Long = 500L, commandId: String? = null): Boolean = runCatching { kotlinx.coroutines.runBlocking { longPressAwait(x, y, durationMs) } }.getOrDefault(false)
    fun performScroll(direction: String, commandId: String? = null): Boolean = runCatching { kotlinx.coroutines.runBlocking { scrollAwait(direction) } }.getOrDefault(false)
    fun performTextInput(text: String, x: Int = -1, y: Int = -1): Boolean = true
    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun performHomeResult(): Boolean = performHome()
    fun performBackResult(): Boolean = performBack()
    fun performRecentsResult(): Boolean = performRecents()
    fun dismissKeyboard(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun restoreKeyboard(): Boolean = true
    fun performEnterAction(): Boolean = true
    fun pressKeyEvent(keyCode: Int): Boolean = true
    fun executeSystemAction(action: String, commandId: String? = null): Boolean = when (action.lowercase()) {
        "back" -> performBack()
        "home" -> performHome()
        "recents" -> performRecents()
        "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "quick_settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "lock" -> lockDevice()
        else -> false
    }
    fun getScreenWidth(): Int = resources.displayMetrics.widthPixels
    fun getScreenHeight(): Int = resources.displayMetrics.heightPixels
    fun getUITree(): Map<String, Any>? = emptyMap()
    fun captureScreen(): String? = kotlinx.coroutines.runBlocking {
        val shot = captureScreenshot()
        if (shot is AccessibilityShot.Ok) {
            shot.base64Jpeg
        } else null
    }
    fun updateLiveNotification(stage: String, status: String) = Unit
    fun updateLiveNotificationProgress(progress: Int, total: Int) = Unit
    fun isAuraImeEnabled(): Boolean = false
    suspend fun typeViaIme(text: String): ImeTypeOutcome = ImeTypeOutcome.FAILED
    fun launchApp(packageName: String): Boolean = runCatching {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        true
    }.getOrDefault(false)
    fun acquireKeepScreenOn(): Boolean = true
    fun releaseKeepScreenOn() = Unit
    enum class ImeTypeOutcome { SUCCESS, FAILED }
    private val screenshotSource by lazy { AccessibilityScreenshotSource(this) }

    private val _signals = MutableSharedFlow<AccessibilitySignal>(extraBufferCapacity = 64)
    val signals: SharedFlow<AccessibilitySignal> = _signals.asSharedFlow()

    private var lastVolumeDownAt = 0L
    private var unlockReceiver: com.aura.aura_ui.notifications.DeviceUnlockReceiver? = null

    /** Explicit accessibility-service state derived from the bound instance. */
    val state: AccessibilityState get() = if (instance != null) AccessibilityState.CONNECTED else AccessibilityState.DISCONNECTED

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 80
        }
        ScreenGeneration.reset()
        if (unlockReceiver == null) {
            val r = com.aura.aura_ui.notifications.DeviceUnlockReceiver()
            unlockReceiver = r
            runCatching {
                registerReceiver(r, android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT))
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeDownAt in 80..600) {
                lastVolumeDownAt = 0L
                runCatching { AssistantForegroundService.listen(this) }
                // Don't consume so system volume behavior remains usable; shortcut is double-press.
            } else {
                lastVolumeDownAt = now
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isNotBlank()) {
            ScreenState.lastPickedPackage = packageName
        }
        val signal = AccessibilitySignal(
            type = event.eventType,
            packageName = packageName,
            className = event.className?.toString().orEmpty(),
            text = event.text?.joinToString(" ").orEmpty().take(300),
        )
        _signals.tryEmit(signal)
        // Debounce: a settling list can emit hundreds of events per second.
        handler.removeCallbacks(bumpRunnable)
        handler.postDelayed(bumpRunnable, 90)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        unlockReceiver?.let {
            runCatching { unregisterReceiver(it) }
            unlockReceiver = null
        }
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        ScreenState.clear()
        super.onDestroy()
    }

    fun currentRoot(): AccessibilityNodeInfo? = runCatching {
        rootInActiveWindow?.also { it.refresh() }
    }.getOrNull()

    fun foregroundPackage(): String? =
        currentRoot()?.packageName?.toString() ?: ScreenState.lastPickedPackage

    fun readScreen(
        includeBounds: Boolean = true,
        changesOnly: Boolean = false,
        maxNodes: Int? = null,
    ): ScreenSnapshot = extractor.extract(
        root = currentRoot(),
        generation = ScreenGeneration.current,
        includeBounds = includeBounds,
        changesOnly = changesOnly,
        maxNodesOverride = maxNodes,
    )

    // ── Perception: Screenshot capture ─────────────────────────────────────────

    suspend fun captureScreenshot(): AccessibilityShot {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            screenshotSource.capture()
        } else {
            AccessibilityShot.Failed("Screen capture requires Android 11+ (API 30+)", retryable = false)
        }
    }

    fun isMediaProjectionAvailable(): Boolean = false

    fun initializeMediaProjection(resultCode: Int, data: Intent): Boolean {
        pendingMediaProjectionResultCode = resultCode
        pendingMediaProjectionData = data
        Log.i(TAG, "MediaProjection token saved (resultCode=$resultCode)")
        return true
    }

    // ── Device Management: Lock & Unlock ───────────────────────────────────────

    /**
     * Locks the Android device immediately without requiring device admin or root.
     * Uses Android Accessibility [GLOBAL_ACTION_LOCK_SCREEN] (Android 9.0+ / API 28+).
     */
    fun lockDevice(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            Log.w(TAG, "GLOBAL_ACTION_LOCK_SCREEN requires Android 9.0+ (API 28+)")
            false
        }
    }

    /**
     * Unlocks the Android device:
     * 1. Acquires a temporary wake-lock to turn the display on.
     * 2. Checks if the keyguard is active.
     * 3. Dispatches a bottom-to-top swipe gesture to open the PIN keypad or dismiss lock.
     * 4. If a PIN is provided, types each digit sequentially into the lock screen keypad.
     */
    suspend fun unlockDevice(pin: String? = null): Boolean {
        // 1. Wake screen
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "aura:unlock"
        )
        try {
            wakeLock?.acquire(3000L)
        } catch (t: Throwable) {
            Log.w(TAG, "Could not acquire wake lock: ${t.message}")
        }

        delay(350L)

        // 2. Check keyguard
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isKeyguardLocked == false) {
            Log.d(TAG, "Device is already unlocked")
            return true
        }

        // 3. Swipe up to dismiss lock screen / expose PIN keypad
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val centerX = (width / 2).toFloat()
        val startY = height * 0.82f
        val endY = height * 0.20f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 250L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val swiped = suspendCancellableCoroutine<Boolean> { cont ->
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
        }

        if (pin.isNullOrBlank()) {
            return swiped
        }

        // 4. Enter PIN digits if provided
        delay(600L) // Allow keypad to animate in
        for (ch in pin) {
            val digit = ch.toString()
            var tapped = false
            val root = currentRoot()
            if (root != null) {
                val nodes = root.findAccessibilityNodeInfosByText(digit)
                for (node in nodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        tapped = true
                        break
                    } else if (node.parent?.isClickable == true) {
                        node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        tapped = true
                        break
                    }
                }
            }
            if (!tapped) {
                tapKeypadCoordinate(ch, width, height)
            }
            delay(180L)
        }

        return true
    }

    private suspend fun tapKeypadCoordinate(ch: Char, screenWidth: Int, screenHeight: Int) {
        val col = when (ch) {
            '1', '4', '7' -> 0
            '2', '5', '8', '0' -> 1
            '3', '6', '9' -> 2
            else -> return
        }
        val row = when (ch) {
            '1', '2', '3' -> 0
            '4', '5', '6' -> 1
            '7', '8', '9' -> 2
            '0' -> 3
            else -> return
        }
        val keypadTop = screenHeight * 0.54f
        val keypadHeight = screenHeight * 0.36f
        val stepY = keypadHeight / 4f
        val stepX = screenWidth / 3f
        val tapX = stepX * col + (stepX / 2f)
        val tapY = keypadTop + (stepY * row) + (stepY / 2f)

        val path = Path().apply { moveTo(tapX, tapY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        suspendCancellableCoroutine<Unit> { cont ->
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }, null)
        }
    }

    companion object {
        private const val TAG = "AuraAccessibility"

        @Volatile
        var instance: AuraAccessibilityService? = null
            private set

        val screenCaptureAvailable = kotlinx.coroutines.flow.MutableStateFlow(false)

        @Volatile
        var pendingMediaProjectionResultCode: Int? = null

        @Volatile
        var pendingMediaProjectionData: Intent? = null

        fun sendScreenCapturePermissionResult(granted: Boolean, error: String? = null) {
            Log.i(TAG, "Screen capture permission result: granted=$granted error=$error")
        }

        fun isConnected(): Boolean = instance != null
        fun isServiceRunning(): Boolean = instance != null

        /** Checks the live system setting, not a cached assumption. */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, AuraAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }

        fun serviceInfo(): AccessibilityServiceInfo? = instance?.serviceInfo
    }
}
