package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class MediaProjectionService : Service() {

    private var mediaProjection: MediaProjection? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "media_projection_channel"
        const val ACTION_START = "ACTION_START_MEDIA_PROJECTION"
        const val ACTION_STOP = "ACTION_STOP_MEDIA_PROJECTION"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"

        var instance: MediaProjectionService? = null
            private set

        private val _isProjectionActive = MutableStateFlow(false)
        val isProjectionActiveFlow: StateFlow<Boolean> = _isProjectionActive.asStateFlow()

        val isProjectionActive: Boolean get() = _isProjectionActive.value

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        suspend fun captureCurrentScreen(): Bitmap? {
            return instance?.captureScreen()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanupProjection()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START) {
            promoteToForeground()
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode == Activity.RESULT_OK && resultData != null) {
                try {
                    val projectionManager =
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection?.stop()
                    val newProjection = projectionManager.getMediaProjection(resultCode, resultData)
                    if (newProjection != null) {
                        newProjection.registerCallback(object : MediaProjection.Callback() {
                            override fun onStop() {
                                super.onStop()
                                mediaProjection = null
                                _isProjectionActive.value = false
                            }
                        }, mainHandler)

                        mediaProjection = newProjection
                        _isProjectionActive.value = true
                    }
                } catch (_: Exception) {
                    _isProjectionActive.value = false
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupProjection()
        instance = null
        _isProjectionActive.value = false
    }

    private fun cleanupProjection() {
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
        _isProjectionActive.value = false
    }

    private fun promoteToForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.media_projection_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps screen search active across other apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.media_projection_notification_title))
            .setContentText(getString(R.string.media_projection_notification_text))
            .setSmallIcon(R.drawable.ic_search)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.Main) {
        val projection = mediaProjection ?: return@withContext null

        suspendCancellableCoroutine { continuation ->
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            var virtualDisplay: VirtualDisplay? = null

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
                    } catch (_: Exception) {
                        if (continuation.isActive) continuation.resume(null)
                    } finally {
                        image.close()
                        imageReader.close()
                        virtualDisplay?.release()
                        // Projection is kept alive!
                    }
                }
            }, mainHandler)

            virtualDisplay = projection.createVirtualDisplay(
                "UniversalFindForegroundCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                mainHandler
            )
        }
    }
}
