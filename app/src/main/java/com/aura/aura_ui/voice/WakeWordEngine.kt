package com.aura.aura_ui.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offline wake word ("hey aura") via sherpa-onnx keyword spotting.
 *
 * The model files live in assets/kws and are copied to the files dir on first use. If they are
 * absent the engine reports [available] = false and the wake service never starts, so a missing
 * model can never strand a microphone foreground service.
 */
class WakeWordEngine private constructor(
    private val spotter: KeywordSpotter,
    val keyword: String,
) {
    private val stream = spotter.createStream()

    fun accept(samples: FloatArray, sampleRate: Int = 16000): Boolean {
        stream.acceptWaveform(samples, sampleRate)
        var hit = false
        while (spotter.isReady(stream)) {
            spotter.decode(stream)
            val result = spotter.getResult(stream)
            if (result.keyword.isNotBlank()) hit = true
            spotter.reset(stream)
        }
        return hit
    }

    fun release() = runCatching {
        stream.release()
        spotter.release()
    }

    companion object {
        /** Model files are expected under assets/kws/<name>/. */
        private val requiredFiles = listOf(
            "encoder.onnx",
            "decoder.onnx",
            "joiner.onnx",
            "tokens.txt",
            "keywords.txt",
        )

        fun modelsPresent(context: Context, modelDir: String = "kws"): Boolean = runCatching {
            val files = context.assets.list(modelDir).orEmpty().toSet()
            requiredFiles.all { it in files }
        }.getOrDefault(false)

        fun ensureExtracted(context: Context, modelDir: String = "kws"): File {
            val target = File(context.filesDir, "kws").also { it.mkdirs() }
            requiredFiles.forEach { name ->
                val out = File(target, name)
                if (!out.exists()) {
                    runCatching {
                        context.assets.open("$modelDir/$name").use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
            return target
        }

        suspend fun create(context: Context, sampleRate: Int = 16000): WakeWordEngine? = withContext(Dispatchers.IO) {
            if (!modelsPresent(context)) return@withContext null
            runCatching {
                val dir = ensureExtracted(context)
                val config = KeywordSpotterConfig(
                    featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = File(dir, "encoder.onnx").absolutePath,
                            decoder = File(dir, "decoder.onnx").absolutePath,
                            joiner = File(dir, "joiner.onnx").absolutePath,
                        ),
                        tokens = File(dir, "tokens.txt").absolutePath,
                        numThreads = 1,
                        modelType = "zipformer2",
                    ),
                    keywordsFile = File(dir, "keywords.txt").absolutePath,
                    keywordsScore = 1.5f,
                    keywordsThreshold = 0.25f,
                    numTrailingBlanks = 1,
                    maxActivePaths = 4,
                )
                WakeWordEngine(KeywordSpotter(config = config), keyword = "hey aura")
            }.getOrNull()
        }
    }
}
