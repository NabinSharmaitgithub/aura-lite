package com.aura.aura_ui.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class AccessibilitySignal(
    val type: Int,
    val packageName: String,
    val className: String,
    val text: String,
    val at: Long = System.currentTimeMillis(),
)

/**
 * The hands of the product.
 *
 * Deliberately small: it exposes a root node, a generation counter and an event stream. All
 * semantics live in [UiTreeExtractor] and [GestureInjector].
 */
class AuraAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val bumpRunnable = Runnable { ScreenGeneration.bump() }

    val extractor = UiTreeExtractor()
    val gestures: GestureInjector by lazy { GestureInjector(this, extractor) }

    private val _signals = MutableSharedFlow<AccessibilitySignal>(extraBufferCapacity = 64)
    val signals: SharedFlow<AccessibilitySignal> = _signals.asSharedFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 80
        }
        ScreenGeneration.reset()
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

    companion object {
        @Volatile
        var instance: AuraAccessibilityService? = null
            private set

        fun isConnected(): Boolean = instance != null

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
