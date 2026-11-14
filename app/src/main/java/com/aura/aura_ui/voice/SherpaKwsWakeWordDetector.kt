package com.aura.aura_ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Free, fully-offline wake-word detection powered by sherpa-onnx keyword spotting (Apache-2.0).
 */
class SherpaKwsWakeWordDetector(
    private val context: Context,
    /** Per-keyword boosting score; higher = easier to trigger. */
    private val keywordsScore: Float = 1.5f,
    /** Trigger threshold 0..1; lower = easier trigger. 0.20 balances recall vs false-accepts. */
    private val keywordsThreshold: Float = 0.20f,
) : WakeWordDetector {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listeningJob: Job? = null
    private var spotter: KeywordSpotter? = null

    private val _isListening = MutableStateFlow(false)
    override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _lastAudioLevel = MutableStateFlow(0f)
    val lastAudioLevel: StateFlow<Float> = _lastAudioLevel.asStateFlow()

    override val availableKeywords: List<String> = listOf("Wake up AURA")

    private var onWakeWordCallback: ((String) -> Unit)? = null
    private var initError: Exception? = null

    init {
        initializeSpotter()
    }

    private fun initializeSpotter() {
        try {
            Log.i(TAG, "🔧 Initializing sherpa-onnx keyword spotter (assets/$ASSET_DIR)…")
            val assetList = context.assets.list(ASSET_DIR).orEmpty().toSet()
            val encoderAsset = if ("encoder.int8.onnx" in assetList) "$ASSET_DIR/encoder.int8.onnx" else "$ASSET_DIR/encoder.onnx"
            val decoderAsset = if ("decoder.int8.onnx" in assetList) "$ASSET_DIR/decoder.int8.onnx" else "$ASSET_DIR/decoder.onnx"
            val joinerAsset = if ("joiner.int8.onnx" in assetList) "$ASSET_DIR/joiner.int8.onnx" else "$ASSET_DIR/joiner.onnx"

            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = encoderAsset,
                    decoder = decoderAsset,
                    joiner = joinerAsset,
                ),
                tokens = "$ASSET_DIR/tokens.txt",
                numThreads = 1,
                provider = "cpu",
                modelType = "zipformer2",
            )
            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig,
                keywordsFile = "$ASSET_DIR/keywords.txt",
                keywordsScore = keywordsScore,
                keywordsThreshold = keywordsThreshold,
            )
            validateKeywordAssets()

            spotter = KeywordSpotter(assetManager = context.assets, config = config)
            initError = null
            Log.i(TAG, "✅ sherpa-onnx keyword spotter initialized (listening for \"Wake up AURA\")")
        } catch (e: Exception) {
            initError = e
            Log.e(TAG, "❌ Failed to initialize sherpa-onnx keyword spotter: ${e.message}", e)
            throw e
        }
    }

    private fun validateKeywordAssets() {
        val vocab: HashSet<String> = context.assets.open("$ASSET_DIR/tokens.txt")
            .bufferedReader().useLines { lines ->
                lines.mapNotNull { it.trim().substringBefore(' ').takeIf(String::isNotEmpty) }
                    .toHashSet()
            }
        context.assets.open("$ASSET_DIR/keywords.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { line ->
                val tokens = line.substringBefore(" :").substringBefore(" #").substringBefore(" @")
                    .trim().split(Regex("\\s+"))
                val missing = tokens.filter { it.isNotEmpty() && it !in vocab }
                check(missing.isEmpty()) {
                    "keywords.txt token(s) $missing not in tokens.txt (line: \"$line\"). Refusing to initialize."
                }
            }
        }
    }

    override fun start() {
        if (_isListening.value) {
            if (listeningJob?.isActive == true) {
                Log.d(TAG, "Already listening (job active)")
                return
            }
            _isListening.value = false
        }

        val activeSpotter = spotter
        if (activeSpotter == null) {
            Log.e(TAG, "❌ Cannot start — spotter not initialized (initError: ${initError?.message})")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "❌ Cannot start — RECORD_AUDIO permission not granted")
            return
        }

        Log.i(TAG, "🎤 Starting wake word detection…")
        _isListening.value = true

        val previous = listeningJob
        listeningJob = scope.launch {
            previous?.join()
            try {
                runDetectionLoop(activeSpotter)
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in wake word detection loop: ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    _isListening.value = false
                    _lastAudioLevel.value = 0f
                    Log.d(TAG, "Listening job completed, isListening=false")
                }
            }
        }
    }

    private suspend fun runDetectionLoop(spotter: KeywordSpotter) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "❌ Invalid AudioRecord buffer size: $minBuffer")
            withContext(Dispatchers.Main) { _isListening.value = false }
            return
        }

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, CHUNK_SAMPLES * 2) * 2,
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "❌ AudioRecord failed to initialize (state=${audioRecord.state})")
            audioRecord.release()
            withContext(Dispatchers.Main) { _isListening.value = false }
            return
        }

        val stream: OnlineStream = spotter.createStream()
        val shortBuf = ShortArray(CHUNK_SAMPLES)
        val floatBuf = FloatArray(CHUNK_SAMPLES)

        try {
            audioRecord.startRecording()
            Log.i(TAG, "✅ Wake word detection active — listening for \"Wake up AURA\"…")

            while (coroutineContext.isActive && _isListening.value) {
                val n = audioRecord.read(shortBuf, 0, CHUNK_SAMPLES)
                if (n <= 0) {
                    if (n < 0) {
                        Log.e(TAG, "AudioRecord read error: $n")
                        break
                    }
                    continue
                }

                // Compute audio peak level for live meter in UI
                var peak = 0
                for (i in 0 until n) {
                    val v = kotlin.math.abs(shortBuf[i].toInt())
                    if (v > peak) peak = v
                    floatBuf[i] = shortBuf[i] / 32768.0f
                }
                _lastAudioLevel.value = (peak / 32768.0f).coerceIn(0f, 1f)

                stream.acceptWaveform(if (n == CHUNK_SAMPLES) floatBuf else floatBuf.copyOf(n), SAMPLE_RATE)

                while (spotter.isReady(stream)) {
                    spotter.decode(stream)
                    val keyword = spotter.getResult(stream).keyword
                    if (keyword.isNotBlank()) {
                        Log.i(TAG, "🔊 Wake word detected: \"$keyword\"")
                        spotter.reset(stream)
                        withContext(Dispatchers.Main) {
                            stopInternal()
                            onWakeWordCallback?.invoke(keyword)
                        }
                        return
                    }
                }
            }
        } finally {
            stream.release()
            runCatching { audioRecord.stop() }
            runCatching { audioRecord.release() }
            Log.d(TAG, "AudioRecord + stream released")
        }
    }

    override fun stop() {
        if (!_isListening.value) return
        Log.i(TAG, "⏹️ Stopping wake word detection…")
        stopInternal()
    }

    private fun stopInternal() {
        _isListening.value = false
        _lastAudioLevel.value = 0f
        listeningJob?.cancel()
    }

    override fun setOnWakeWordDetected(callback: (String) -> Unit) {
        onWakeWordCallback = callback
    }

    override fun release() {
        stop()
        onWakeWordCallback = null
        spotter?.release()
        spotter = null
        Log.d(TAG, "SherpaKwsWakeWordDetector released")
    }

    companion object {
        private const val TAG = "SherpaKwsDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SAMPLES = 1600
        private const val ASSET_DIR = "kws"
    }
}
