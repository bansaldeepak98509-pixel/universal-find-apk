package com.example.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import com.example.service.MediaProjectionService
import kotlinx.coroutines.delay

class ScreenCaptureManager(private val context: Context) {

    private val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    fun createScreenCaptureIntent(): Intent = projectionManager.createScreenCaptureIntent()

    val isProjectionActive: Boolean
        get() = MediaProjectionService.isProjectionActive

    suspend fun captureScreen(
        resultCode: Int,
        resultData: Intent
    ): Bitmap? {
        MediaProjectionService.start(context, resultCode, resultData)
        var retries = 0
        while (!MediaProjectionService.isProjectionActive && retries < 10) {
            delay(100)
            retries++
        }
        return MediaProjectionService.captureCurrentScreen()
    }

    suspend fun captureCurrentScreen(): Bitmap? {
        return MediaProjectionService.captureCurrentScreen()
    }

    fun stopScreenCapture() {
        MediaProjectionService.stop(context)
    }
}
