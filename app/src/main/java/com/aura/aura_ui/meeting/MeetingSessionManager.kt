package com.aura.aura_ui.meeting

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.aura.aura_ui.voice.VoiceEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class MeetingState {
    IDLE,
    RECORDING,
    PROMPTING_SUMMARY,
    SUMMARIZING,
    COMPLETED
}

@Singleton
class MeetingSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voice: VoiceEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(MeetingState.IDLE)
    val state: StateFlow<MeetingState> = _state.asStateFlow()

    private val _liveTranscript = MutableStateFlow<List<String>>(emptyList())
    val liveTranscript: StateFlow<List<String>> = _liveTranscript.asStateFlow()

    private val _latestLine = MutableStateFlow("")
    val latestLine: StateFlow<String> = _latestLine.asStateFlow()

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile.asStateFlow()

    private val _summaryResult = MutableStateFlow<String?>(null)
    val summaryResult: StateFlow<String?> = _summaryResult.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var fileWriter: FileWriter? = null
    private var isContinuousRunning = false

    companion object {
        private const val TAG = "MeetingSessionManager"
    }

    fun startMeeting(title: String = "Meeting") {
        if (_state.value == MeetingState.RECORDING) return

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = File(context.getExternalFilesDir(null), "Meetings").apply { mkdirs() }
        val noteFile = File(dir, "Meeting_${timeStamp}.txt")

        runCatching {
            fileWriter = FileWriter(noteFile, true).apply {
                write("=== Meeting Notes: $title ===\n")
                write("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
                flush()
            }
        }

        _currentFile.value = noteFile
        _liveTranscript.value = emptyList()
        _summaryResult.value = null
        _state.value = MeetingState.RECORDING
        isContinuousRunning = true

        voice.speak("Meeting recording started. I am transcribing notes in real time. Tell me when the meeting is over.")

        mainHandler.postDelayed({
            if (isContinuousRunning) {
                beginListeningLoop()
            }
        }, 3000L)
    }

    private fun beginListeningLoop() {
        if (!isContinuousRunning || _state.value != MeetingState.RECORDING) return

        mainHandler.post {
            runCatching {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        if (isContinuousRunning && _state.value == MeetingState.RECORDING) {
                            mainHandler.postDelayed({ beginListeningLoop() }, 600L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            handleTranscriptChunk(text)
                        }
                        if (isContinuousRunning && _state.value == MeetingState.RECORDING) {
                            mainHandler.postDelayed({ beginListeningLoop() }, 400L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            _latestLine.value = text
                            if (isMeetingOverTrigger(text)) {
                                endMeeting()
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            }.onFailure { e ->
                Log.e(TAG, "Failed to start speech recognizer loop: ${e.message}", e)
            }
        }
    }

    private fun handleTranscriptChunk(text: String) {
        if (isMeetingOverTrigger(text)) {
            endMeeting()
            return
        }

        val timeTag = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$timeTag] $text"

        _latestLine.value = text
        _liveTranscript.value = _liveTranscript.value + line

        runCatching {
            fileWriter?.apply {
                write("$line\n")
                flush()
            }
        }
    }

    private fun isMeetingOverTrigger(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("meeting is over") ||
                lower.contains("meeting is finished") ||
                lower.contains("meeting is done") ||
                lower.contains("end the meeting") ||
                lower.contains("stop the meeting") ||
                lower.contains("finish the meeting")
    }

    fun endMeeting() {
        if (!isContinuousRunning && _state.value != MeetingState.RECORDING) return

        isContinuousRunning = false
        mainHandler.post {
            runCatching {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            }
        }

        runCatching {
            fileWriter?.apply {
                write("\n=== End of Meeting ===\n")
                flush()
                close()
            }
            fileWriter = null
        }

        _state.value = MeetingState.PROMPTING_SUMMARY

        val askText = "The meeting is over and your notes have been saved. Would you like me to summarize the meeting notes?"
        voice.speak(askText)
    }

    fun respondToSummary(shouldSummarize: Boolean, summarizerFn: suspend (String) -> String = { text -> fallbackSummarize(text) }) {
        if (_state.value != MeetingState.PROMPTING_SUMMARY) return

        if (!shouldSummarize) {
            _state.value = MeetingState.COMPLETED
            voice.speak("Saved exact meeting notes.")
            return
        }

        _state.value = MeetingState.SUMMARIZING
        voice.speak("Summarizing your meeting notes now.")

        scope.launch {
            val rawNotes = _liveTranscript.value.joinToString("\n")
            val summary = summarizerFn(rawNotes)
            _summaryResult.value = summary

            val file = _currentFile.value
            if (file != null) {
                val summaryFile = File(file.parentFile, file.nameWithoutExtension + "_Summary.txt")
                runCatching {
                    summaryFile.writeText("=== Meeting Summary ===\nGenerated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n$summary\n\n=== Raw Notes ===\n$rawNotes")
                }
            }

            _state.value = MeetingState.COMPLETED
            voice.speak("Here is the meeting summary. Notes and summary have been saved.")
        }
    }

    private fun fallbackSummarize(rawText: String): String {
        val lines = rawText.lines().filter { it.isNotBlank() }
        val bulletPoints = lines.takeLast(10).joinToString("\n") { "• " + it.substringAfter("] ").trim() }
        return "Meeting Highlights (${lines.size} notes captured):\n$bulletPoints\n\nStatus: Successfully recorded and archived."
    }

    fun reset() {
        isContinuousRunning = false
        runCatching {
            speechRecognizer?.destroy()
            speechRecognizer = null
            fileWriter?.close()
            fileWriter = null
        }
        _state.value = MeetingState.IDLE
        _liveTranscript.value = emptyList()
        _latestLine.value = ""
        _summaryResult.value = null
    }
}
