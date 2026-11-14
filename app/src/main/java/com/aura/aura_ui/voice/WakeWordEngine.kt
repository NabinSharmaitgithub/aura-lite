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
 * Offline wake word via sherpa-onnx keyword spotting.
 *
 * Default phrase target: "Wake up AURA" (configured via assets/kws/keywords.txt in the
 * bundled sherpa-onnx KWS model). Supports both int8 quantized and fp32 model variants.
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
        fun modelsPresent(context: Context, modelDir: String = "kws"): Boolean = runCatching {
            val files = context.assets.list(modelDir).orEmpty().toSet()
            val hasEncoder = "encoder.int8.onnx" in files || "encoder.onnx" in files
            val hasDecoder = "decoder.int8.onnx" in files || "decoder.onnx" in files
            val hasJoiner = "joiner.int8.onnx" in files || "joiner.onnx" in files
            val hasTokens = "tokens.txt" in files
            val hasKeywords = "keywords.txt" in files
            hasEncoder && hasDecoder && hasJoiner && hasTokens && hasKeywords
        }.getOrDefault(false)

        fun ensureExtracted(context: Context, modelDir: String = "kws"): File {
            val target = File(context.filesDir, "kws").also { it.mkdirs() }
            val assetFiles = context.assets.list(modelDir).orEmpty().toSet()

            val encoderSrc = if ("encoder.int8.onnx" in assetFiles) "encoder.int8.onnx" else "encoder.onnx"
            val decoderSrc = if ("decoder.int8.onnx" in assetFiles) "decoder.int8.onnx" else "decoder.onnx"
            val joinerSrc = if ("joiner.int8.onnx" in assetFiles) "joiner.int8.onnx" else "joiner.onnx"

            val filesToExtract = listOf(
                encoderSrc to "encoder.onnx",
                decoderSrc to "decoder.onnx",
                joinerSrc to "joiner.onnx",
                "tokens.txt" to "tokens.txt",
                "keywords.txt" to "keywords.txt",
            )
            filesToExtract.forEach { (src, dst) ->
                val out = File(target, dst)
                if (!out.exists() || out.length() == 0L || dst == "keywords.txt") {
                    runCatching {
                        context.assets.open("$modelDir/$src").use { input ->
                            out.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
            return target
        }

        fun configuredKeywordLabel(context: Context, modelDir: String = "kws"): String {
            val fromAssets = runCatching {
                context.assets.open("$modelDir/keywords.txt").bufferedReader().use { br ->
                    br.readLines().firstOrNull { it.isNotBlank() }?.substringBefore("@")?.trim()
                }
            }.getOrNull()
            val fromFiles = runCatching {
                val f = File(context.filesDir, "kws/keywords.txt")
                if (f.exists()) f.readLines().firstOrNull { it.isNotBlank() }?.substringBefore("@")?.trim() else null
            }.getOrNull()
            return (fromAssets ?: fromFiles)?.ifBlank { null } ?: "Wake up AURA"
        }

        suspend fun create(
            context: Context,
            sampleRate: Int = 16000,
            threshold: Float = 0.20f,
            score: Float = 1.5f,
        ): WakeWordEngine? = withContext(Dispatchers.IO) {
            if (!modelsPresent(context)) return@withContext null
            runCatching {
                val dir = ensureExtracted(context)
                val keywordLabel = configuredKeywordLabel(context)
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
                    keywordsScore = score,
                    keywordsThreshold = threshold,
                    numTrailingBlanks = 1,
                    maxActivePaths = 4,
                )
                WakeWordEngine(KeywordSpotter(config = config), keyword = keywordLabel)
            }.getOrNull()
        }
    }
}
