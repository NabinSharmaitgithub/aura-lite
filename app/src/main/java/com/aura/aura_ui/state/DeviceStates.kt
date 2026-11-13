package com.aura.aura_ui.state

/** Speech-recognition lifecycle. TTS readiness is tracked separately. */
enum class VoiceSessionState {
    IDLE,
    LISTENING,
}

/** Text-to-speech engine lifecycle. */
enum class TtsState {
    UNINITIALIZED,
    INITIALIZING,
    READY,
    FAILED,
}

/** Offline wake-word detector lifecycle. */
enum class WakeWordState {
    STOPPED,
    STARTING,
    DETECTING,
    PAUSED_FOR_RUN,
    STOPPING,
}

/** Visual state of the floating assistant pill. */
enum class PillState {
    IDLE,
    LISTENING,
    SPEAKING,
}

/** MediaProjection consent/capture lifecycle. */
sealed interface CaptureState {
    data object NoConsent : CaptureState
    data class Requested(val token: Int) : CaptureState
    data object Granted : CaptureState
    data object Capturing : CaptureState
    data object Released : CaptureState

    val hasConsent: Boolean get() = this == Granted || this == Capturing
}

/** Browser controller lifecycle. */
enum class BrowserState {
    DETACHED,
    ATTACHED,
    LOADING,
    LOADED,
    HANDOFF,
    DESTROYED,
}

/** Accessibility service presence. */
enum class AccessibilityState {
    DISCONNECTED,
    CONNECTED,
}

/** Keyboard lease state, with a real reference count. */
data class KeyboardLeaseState(
    val holders: Int = 0,
    val owner: String? = null,
) {
    val held: Boolean get() = holders > 0
}

/** Freshness of the last extracted screen snapshot. */
enum class ScreenFreshness {
    EMPTY,
    FRESH,
    STALE,
}

/** App install hand-off lifecycle. The final two states happen inside the Play Store. */
enum class InstallState {
    IDLE,
    RESOLVING,
    UNRESOLVED,
    LISTING_OPENED,
    FAILED,
}

/** Foreground service lifecycle. */
enum class ForegroundServiceState {
    CREATED,
    RUNNING,
    LISTENING,
    STOPPED,
}

/** MediaProjection foreground service lifecycle. */
enum class ProjectionServiceState {
    CREATED,
    FOREGROUND,
    STOPPED,
}
