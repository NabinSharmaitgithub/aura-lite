package com.aura.aura_ui.perception

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optional on-device perception.
 *
 * OCR ships with ML Kit and is always available. Icon detection is an ONNX model loaded only on
 * first use, used for one call, then closed. This is the single biggest avoidable RSS cost, so
 * the session is never created at startup.
 */
@Singleton
class PerceptionEngine @Inject constructor(@ApplicationContext private val context: Context) {

    private val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)
    @Volatile private var session: OrtSession? = null
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var loadFailure: String? = null

    suspend fun perceive(file: File, mode: String, maxDimension: Int = 1080): JsonObject = withContext(Dispatchers.Default) {
        if (!busy.compareAndSet(false, true)) {
            return@withContext buildJsonObject { put("error", "Perception is already running") }
        }
        var bitmap: Bitmap? = null
        try {
            bitmap = decodeDownscaled(file, maxDimension)
                ?: return@withContext buildJsonObject { put("error", "Could not decode frame") }
            val wantOcr = mode == "ocr" || mode == "both"
            val wantIcons = mode == "icons" || mode == "both"
            val ocrText = if (wantOcr) runOcr(bitmap) else ""
            val icons = if (wantIcons) detectIcons(bitmap) else emptyList()
            buildJsonObject {
                put("source", file.name)
                put("width", bitmap.width)
                put("height", bitmap.height)
                if (wantOcr) {
                    put("ocr_text", ocrText)
                    put("ocr_blocks", buildJsonArray { })
                }
                if (wantIcons) {
                    put(
                        "icons",
                        buildJsonArray {
                            icons.forEach { icon ->
                                add(buildJsonObject {
                                    put("x", icon.x)
                                    put("y", icon.y)
                                    put("w", icon.w)
                                    put("h", icon.h)
                                    put("label", icon.label)
                                })
                            }
                        },
                    )
                    put("icon_model", if (session != null) "loaded" else (loadFailure ?: "not_bundled"))
                }
                put(
                    "note",
                    "Merge icon boxes with the accessibility tree; prefer tree indices when both exist.",
                )
            }
        } finally {
            bitmap?.recycle()
            busy.set(false)
        }
    }

    private suspend fun runOcr(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        ocr.process(image)
            .addOnSuccessListener { result ->
                if (cont.isActive) cont.resume(result.text)
            }
            .addOnFailureListener { error ->
                if (cont.isActive) cont.resumeWithException(error)
            }
    }

    private fun detectIcons(bitmap: Bitmap): List<IconBox> {
        val active = session ?: loadSession() ?: return emptyList()
        val env = env ?: return emptyList()
        val inputName = active.inputNames.firstOrNull() ?: return emptyList()
        val size = 640
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val data = FloatArray(3 * size * size)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        scaled.recycle()
        for (i in pixels.indices) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF) / 255f
            data[size * size + i] = ((p shr 8) and 0xFF) / 255f
            data[2 * size * size + i] = (p and 0xFF) / 255f
        }
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, size.toLong(), size.toLong()))
        return try {
            val output = active.run(mapOf(inputName to tensor))
            output.use { result ->
                val first = result[0] as? OnnxTensor ?: return emptyList()
                parseDetections(first, bitmap.width, bitmap.height, size)
            }
        } catch (t: Throwable) {
            loadFailure = t.message
            emptyList()
        } finally {
            tensor.close()
        }
    }

    private fun parseDetections(tensor: OnnxTensor, originalW: Int, originalH: Int, size: Int): List<IconBox> {
        val shape = tensor.info.shape
        val raw = tensor.value
        val boxes = mutableListOf<IconBox>()
        // YOLO-style [1, 4+classes, N] or [1, N, 4+classes].
        val matrix = flatten(raw) ?: return emptyList()
        val channels: Int
        val count: Int
        if (shape.size >= 3 && shape[1] < shape[2]) {
            channels = shape[1].toInt()
            count = shape[2].toInt()
        } else if (shape.size >= 3) {
            channels = shape[2].toInt()
            count = shape[1].toInt()
            // Transposed layout is skipped; the bundled model uses channels-first.
        } else {
            return emptyList()
        }
        val sx = originalW.toFloat() / size
        val sy = originalH.toFloat() / size
        for (i in 0 until count) {
            val base = i
            val cx = matrix[base] * sx
            val cy = matrix[count + base] * sy
            val w = matrix[2 * count + base] * sx
            val h = matrix[3 * count + base] * sy
            var best = 0f
            for (c in 4 until channels) {
                val score = matrix[c * count + base]
                if (score > best) best = score
            }
            if (best >= 0.35f) {
                boxes += IconBox(
                    x = (cx - w / 2).toInt(),
                    y = (cy - h / 2).toInt(),
                    w = w.toInt(),
                    h = h.toInt(),
                    label = "icon",
                )
            }
        }
        return boxes.sortedByDescending { it.left }.take(40)
    }

    private fun flatten(value: Any?): FloatArray? {
        val out = ArrayList<Float>(3 * 640 * 640)
        fun walk(node: Any?) {
            when (node) {
                is FloatArray -> node.forEach { out.add(it) }
                is Array<*> -> node.forEach { walk(it) }
            }
        }
        walk(value)
        return if (out.isEmpty()) null else out.toFloatArray()
    }

    private fun loadSession(): OrtSession? {
        if (loadFailure != null) return null
        return runCatching {
            val available = context.assets.list("models").orEmpty().any { it == MODEL }
            if (!available) {
                loadFailure = "not_bundled"
                return null
            }
            val env = OrtEnvironment.getEnvironment()
            val bytes = context.assets.open("models/$MODEL").use { it.readBytes() }
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val session = env.createSession(bytes, options)
            this.env = env
            this.session = session
            session
        }.getOrElse {
            loadFailure = it.message
            null
        }
    }

    /** Called when a run ends. Model memory returns to baseline. */
    fun release() {
        // A parallel perceive call may still be using the session; it releases on its next idle.
        if (busy.get()) return
        runCatching { session?.close() }
        session = null
        loadFailure = null
    }

    private fun decodeDownscaled(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private data class IconBox(val x: Int, val y: Int, val w: Int, val h: Int, val label: String) {
        val left: Int get() = x
    }

    private companion object {
        const val MODEL = "omniparser_icon_detect.onnx"
    }
}
