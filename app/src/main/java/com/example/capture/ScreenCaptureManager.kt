package com.example.capture

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
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScreenCaptureManager(private val context: Context) {

    private val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    fun createScreenCaptureIntent(): Intent = projectionManager.createScreenCaptureIntent()

    suspend fun captureScreen(
        resultCode: Int,
        resultData: Intent
    ): Bitmap? = suspendCancellableCoroutine { continuation ->
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val projection: MediaProjection? = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        val handler = Handler(Looper.getMainLooper())

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val cropped = if (rowPadding > 0) {
                        Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    } else {
                        bitmap
                    }

                    if (continuation.isActive) {
                        continuation.resume(cropped)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                } finally {
                    image.close()
                    imageReader.close()
                    virtualDisplay?.release()
                    projection.stop()
                }
            }
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "UniversalFindCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            handler
        )
    }
}
