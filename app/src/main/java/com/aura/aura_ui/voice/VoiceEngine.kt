package com.aura.aura_ui.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aura.aura_ui.state.TtsState
import com.aura.aura_ui.state.VoiceSessionState
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Single-owner voice pipeline: one recorder, one recognizer, one speaker.
 *
 * STT uses the platform recognizer (on-device where the OEM provides it). TTS uses the platform
 * engine and can target a specific engine package/voice (e.g. Google TTS) when configured.
 * Wake-word and VAD live in their own engines.
 */
@Singleton
class VoiceEngine @Inject constructor(@ApplicationContext private val context: Context) {

    private val owner = AtomicBoolean(false)
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsEnginePackage: String? = null

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _session = MutableStateFlow(VoiceSessionState.IDLE)
    /** Explicit speech-recognition lifecycle; [listening] is derived from it. */
    val session: StateFlow<VoiceSessionState> = _session.asStateFlow()

    private val _ttsState = MutableStateFlow(TtsState.UNINITIALIZED)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private fun setSession(state: VoiceSessionState) {
        _session.value = state
        _listening.value = state == VoiceSessionState.LISTENING
    }

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    suspend fun listen(timeoutMs: Long = 15000): Result<String> {
        if (!owner.compareAndSet(false, true)) {
            return Result.failure(IllegalStateException("Voice capture is already in use"))
        }
        _transcript.value = ""
        setSession(VoiceSessionState.LISTENING)
        return try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    var settled = false
                    fun finish(result: Result<String>) {
                        if (settled) return
                        settled = true
                        setSession(VoiceSessionState.IDLE)
                        owner.set(false)
                        recognizer?.destroy()
                        recognizer = null
                        if (cont.isActive) cont.resume(result)
                    }

                    val speech = SpeechRecognizer.createSpeechRecognizer(context)
                    recognizer = speech
                    speech.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onError(error: Int) {
                            finish(Result.failure(IllegalStateException(errorText(error))))
                        }
                        override fun onResults(results: Bundle?) {
                            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull().orEmpty()
                            _transcript.value = text
                            finish(
                                if (text.isBlank()) Result.failure(IllegalStateException("I didn't catch that."))
                                else Result.success(text),
                            )
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.let { _transcript.value = it }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    }
                    speech.startListening(intent)
                    cont.invokeOnCancellation {
                        runCatching { speech.cancel() }
                        runCatching { speech.destroy() }
                        recognizer = null
                        setSession(VoiceSessionState.IDLE)
                        owner.set(false)
                    }
                }
            }
        } catch (t: Throwable) {
            setSession(VoiceSessionState.IDLE)
            owner.set(false)
            Result.failure(t)
        }
    }

    fun cancelListening() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        setSession(VoiceSessionState.IDLE)
        owner.set(false)
    }

    fun speak(text: String, flush: Boolean = true) {
        val engine = ensureTts() ?: return
        if (!ttsReady) return
        // Apply preferred voice from settings each time (lightweight) in case user changed it.
        applyPreferredVoice(engine)
        _speaking.value = true
        engine.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "aura-${System.nanoTime()}")
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        _speaking.value = false
    }

    /** Re-resolve and re-apply the persisted TTS preference (engine + voice) on the live engine. */
    fun refreshTtsVoice(): Boolean {
        val engine = ensureTts() ?: return false
        applyPreferredVoice(engine)
        return true
    }

    fun voices(): List<String> = runCatching { ensureTts(); tts?.voices?.map { it.name }.orEmpty() }.getOrDefault(emptyList())

    fun availableEngines(): List<String> = runCatching {
        ensureTts()
        tts?.engines?.map { it.name }.orEmpty()
    }.getOrDefault(emptyList())

    fun shutdown() {
        cancelListening()
        runCatching { tts?.shutdown() }
        tts = null
        ttsReady = false
        ttsEnginePackage = null
        _ttsState.value = TtsState.UNINITIALIZED
    }

    private fun preferredTtsSetting(): String =
        context.getSharedPreferences("aura_settings", Context.MODE_PRIVATE)
            .getString("tts_voice", "").orEmpty().trim()

    private fun resolvePreferredEnginePackage(setting: String): String? {
        if (setting.isBlank()) return null
        // Accept explicit engine package or common aliases
        val lower = setting.lowercase()
        return when {
            lower == "google" || lower == "google_tts" || lower == "com.google.android.tts" -> "com.google.android.tts"
            setting.contains('.') -> setting.substringBefore('|').substringBefore('#')
            else -> null
        }
    }

    private fun applyPreferredVoice(engine: TextToSpeech) {
        val pref = preferredTtsSetting()
        if (pref.isBlank()) {
            runCatching { engine.language = Locale.getDefault() }
            return
        }
        val voiceName = pref.substringAfter('|', "").substringAfter('#', "").ifBlank { null }
        if (voiceName != null) {
            runCatching {
                val voice = engine.voices?.firstOrNull { it.name == voiceName || it.name.equals(voiceName, true) }
                if (voice != null) engine.voice = voice
            }
        } else {
            runCatching { engine.language = Locale.getDefault() }
        }
    }

    private fun ensureTts(): TextToSpeech? {
        val pref = preferredTtsSetting()
        val desiredEngine = resolvePreferredEnginePackage(pref)
        if (tts != null && desiredEngine == ttsEnginePackage) return tts
        if (tts != null && desiredEngine != ttsEnginePackage) {
            runCatching { tts?.shutdown() }
            tts = null
            ttsReady = false
            _ttsState.value = TtsState.UNINITIALIZED
        }
        if (tts == null) _ttsState.value = TtsState.INITIALIZING
        ttsEnginePackage = desiredEngine
        tts = if (desiredEngine != null) {
            TextToSpeech(context, { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                _ttsState.value = if (ttsReady) TtsState.READY else TtsState.FAILED
                if (ttsReady) {
                    tts?.let { engine ->
                        applyPreferredVoice(engine)
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) { _speaking.value = true }
                            override fun onDone(utteranceId: String?) { _speaking.value = false }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) { _speaking.value = false }
                        })
                    }
                }
            }, desiredEngine)
        } else {
            TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                _ttsState.value = if (ttsReady) TtsState.READY else TtsState.FAILED
                if (ttsReady) {
                    tts?.let { engine ->
                        applyPreferredVoice(engine)
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) { _speaking.value = true }
                            override fun onDone(utteranceId: String?) { _speaking.value = false }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) { _speaking.value = false }
                        })
                    }
                }
            }
        }
        return tts
    }

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection."
        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recognizer is busy."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
        else -> "Speech recognition failed ($code)."
    }
}

/** Energy VAD used to avoid running wake-word inference on silence. */
class VadGate(private val sensitivity: Int = 2) {
    private val threshold: Int = when (sensitivity.coerceIn(0, 4)) {
        0 -> 1800
        1 -> 1300
        2 -> 900
        3 -> 650
        else -> 450
    }

    fun isSpeech(frame: ShortArray): Boolean {
        if (frame.isEmpty()) return false
        var sum = 0L
        for (sample in frame) sum += sample.toLong() * sample
        val rms = kotlin.math.sqrt(sum.toDouble() / frame.size)
        return rms > threshold
    }

    fun rms(frame: ShortArray): Int {
        if (frame.isEmpty()) return 0
        var sum = 0L
        for (sample in frame) sum += sample.toLong() * sample
        return kotlin.math.sqrt(sum.toDouble() / frame.size).toInt()
    }
}
