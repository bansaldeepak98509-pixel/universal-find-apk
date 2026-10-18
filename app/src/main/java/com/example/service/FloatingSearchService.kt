package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.accessibility.AccessibleTextProvider
import com.example.search.SearchEngine
import com.example.search.SearchQuery
import com.example.search.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class FloatingSearchService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var windowManager: WindowManager

    private var floatingBubbleView: View? = null
    private var searchOverlayBarView: View? = null

    private val searchEngine = SearchEngine()
    private val accessibleTextProvider = AccessibleTextProvider(searchEngine)
    private var currentResult = SearchResult(query = SearchQuery(""))

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            if (fgsType != 0) {
                startForeground(NOTIFICATION_ID, createNotification(), fgsType)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        createFloatingBubble()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground notification for Universal Find floating search"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_service_notification_title))
            .setContentText(getString(R.string.floating_service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        if (floatingBubbleView != null) return

        val bubble = TextView(this).apply {
            text = "🔍"
            textSize = 22f
            gravity = Gravity.CENTER
            setBackgroundColor(0xEE1E293B.toInt())
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                            isClick = false
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(bubble, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleFloatingSearchBar()
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingBubbleView = bubble
        try {
            windowManager.addView(bubble, params)
        } catch (_: Exception) {
            // Permission might have been revoked
        }
    }

    private fun toggleFloatingSearchBar() {
        if (searchOverlayBarView != null) {
            removeSearchBar()
        } else {
            showSearchBar()
        }
    }

    private fun showSearchBar() {
        // Simple and clean find bar layout
        val barContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(0xF01E293B.toInt())
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER_VERTICAL
        }

        val searchInput = EditText(this).apply {
            hint = "Find on screen…"
            setHintTextColor(0x88FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 14f
            background = null
            setSingleLine(true)
            layoutParams = android.widget.LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f)
        }

        val counterView = TextView(this).apply {
            text = "0/0"
            setTextColor(0xFFCBD5E1.toInt())
            textSize = 12f
            setPadding(8, 0, 8, 0)
        }

        val prevBtn = TextView(this).apply {
            text = "▲"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(12, 8, 12, 8)
            setOnClickListener {
                currentResult = searchEngine.previousMatch(currentResult)
                counterView.text = currentResult.displayCounter
            }
        }

        val nextBtn = TextView(this).apply {
            text = "▼"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(12, 8, 12, 8)
            setOnClickListener {
                currentResult = searchEngine.nextMatch(currentResult)
                counterView.text = currentResult.displayCounter
            }
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(0xFFEF4444.toInt())
            textSize = 16f
            setPadding(12, 8, 12, 8)
            setOnClickListener {
                removeSearchBar()
            }
        }

        barContainer.addView(searchInput)
        barContainer.addView(counterView)
        barContainer.addView(prevBtn)
        barContainer.addView(nextBtn)
        barContainer.addView(closeBtn)

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString().orEmpty()
                if (q.isBlank()) {
                    currentResult = SearchResult(query = SearchQuery(""))
                    counterView.text = ""
                } else {
                    currentResult = accessibleTextProvider.searchActiveScreen(SearchQuery(q))
                    counterView.text = currentResult.displayCounter
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 100
        }

        searchOverlayBarView = barContainer
        try {
            windowManager.addView(barContainer, barParams)
        } catch (_: Exception) {}
    }

    private fun removeSearchBar() {
        searchOverlayBarView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            searchOverlayBarView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeSearchBar()
        floatingBubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            floatingBubbleView = null
        }
    }

    companion object {
        const val CHANNEL_ID = "universal_find_floating_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, FloatingSearchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingSearchService::class.java)
            context.stopService(intent)
        }
    }
}
