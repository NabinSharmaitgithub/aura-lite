package com.aura.aura_ui.accessibility

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aura.aura_ui.state.CaptureState
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Single-frame MediaProjection capture with automatic fallback to AccessibilityScreenshot.
 *
 * Memory contract: at most one full-resolution frame exists, the virtual display is torn down
 * immediately after each capture, and the returned bitmap is downscaled and recycled by callers.
 */
@Singleton
class ScreenCaptureManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        @Volatile private var pendingResultCode: Int? = null
        @Volatile private var pendingResultData: Intent? = null

        fun onExternalConsentGranted(appContext: Context, resultCode: Int, data: Intent) {
            pendingResultCode = resultCode
            pendingResultData = Intent(data)
            // Best-effort: if a Hilt instance isn't reachable, VM/UI paths can still consume via pending stash
            // by creating projection later through AccessibilityService-stashed token fallback.
        }

        fun onExternalConsentDenied() {
            pendingResultCode = null
            pendingResultData = null
        }
    }

    private fun consumePendingExternalConsent() {
        val code = pendingResultCode ?: return
        val data = pendingResultData ?: return
        pendingResultCode = null
        pendingResultData = null
        handleConsentResult(code, data)
    }

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private val mutex = Mutex()
    private val _consentRequested = MutableStateFlow(0)
    val consentRequested: StateFlow<Int> = _consentRequested.asStateFlow()

    private val _state = MutableStateFlow<CaptureState>(CaptureState.NoConsent)
    /** Explicit consent/capture lifecycle; [consentRequested] is the UI launch token. */
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    private var requestToken = 0

    fun createConsentIntent(): Intent = projectionManager.createScreenCaptureIntent()

    fun requestConsent() {
        requestToken++
        _state.value = CaptureState.Requested(requestToken)
        _consentRequested.value = requestToken
    }

    fun handleConsentResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            _state.value = CaptureState.NoConsent
            _consentRequested.value = 0
            return
        }
        release()
        runCatching {
            projection = projectionManager.getMediaProjection(resultCode, data)
            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    projection = null
                    _state.value = CaptureState.NoConsent
                }
            }
            if (Build.VERSION.SDK_INT >= 34) {
                projectionCallback?.let { projection?.registerCallback(it, Handler(context.mainLooper)) }
            }
            _state.value = CaptureState.Granted
        }.onFailure { e ->
            Log.e("ScreenCaptureManager", "Failed to obtain MediaProjection: ${e.message}", e)
            // If MediaProjection fails, mark granted if Accessibility service is running as fallback
            _state.value = if (AuraAccessibilityService.isConnected()) CaptureState.Granted else CaptureState.NoConsent
        }
        _consentRequested.value = 0
    }

    fun hasConsent(): Boolean {
        consumePendingExternalConsent()
        return _state.value.hasConsent || AuraAccessibilityService.isConnected()
    }

    suspend fun captureToFile(maxDimension: Int = 1080): Result<File> = mutex.withLock {
        consumePendingExternalConsent()
        val active = projection
        if (active == null) {
            // Fallback to accessibility screenshot source
            val service = AuraAccessibilityService.instance
            if (service != null) {
                return when (val shot = service.captureScreenshot()) {
                    is AccessibilityShot.Ok -> {
                        val bytes = Base64.decode(shot.base64Jpeg, Base64.DEFAULT)
                        val out = File(context.filesDir, "screen/current.png").also { it.parentFile?.mkdirs() }
                        out.writeBytes(bytes)
                        Result.success(out)
                    }
                    is AccessibilityShot.Failed -> Result.failure(IllegalStateException(shot.message))
                }
            }
            return Result.failure(
                IllegalStateException("Screen capture permission has not been granted and accessibility service is inactive"),
            )
        }

        _state.value = CaptureState.Capturing
        val (width, height) = ScreenGeometry.realSizePx(context)
        val density = ScreenGeometry.densityDpi(context)
        val thread = HandlerThread("aura-capture").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var display: VirtualDisplay? = null

        try {
            display = active.createVirtualDisplay(
                "aura-capture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler,
            )

            val bitmap = suspendCancellableCoroutine<Bitmap?> { cont ->
                reader.setOnImageAvailableListener({ r ->
                    val image = runCatching { r.acquireLatestImage() }.getOrNull()
                    if (image == null) {
                        if (cont.isActive) cont.resume(null)
                        return@setOnImageAvailableListener
                    }
                    try {
                        val plane = image.planes[0]
                        val rowStride = plane.rowStride
                        val pixelStride = plane.pixelStride
                        val rowWidth = rowStride / pixelStride
                        val padded = Bitmap.createBitmap(rowWidth, image.height, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val cropped = Bitmap.createBitmap(padded, 0, 0, width.coerceAtMost(rowWidth), image.height)
                        if (cropped !== padded) padded.recycle()
                        if (cont.isActive) cont.resume(cropped)
                    } catch (t: Throwable) {
                        if (cont.isActive) cont.resume(null)
                    } finally {
                        image.close()
                    }
                }, handler)
            } ?: return Result.failure(IllegalStateException("No frame was produced"))

            val scaled = downscale(bitmap, maxDimension)
            val out = File(context.filesDir, "screen/current.png").also { it.parentFile?.mkdirs() }
            FileOutputStream(out).use { stream ->
                scaled.compress(Bitmap.CompressFormat.PNG, 92, stream)
            }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            Result.success(out)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            runCatching { display?.release() }
            runCatching { reader.close() }
            thread.quitSafely()
            _state.value = if (projection != null) CaptureState.Granted else CaptureState.NoConsent
        }
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    fun release() {
        runCatching { projection?.stop() }
        projection = null
        projectionCallback = null
        _state.value = CaptureState.NoConsent
    }
}
