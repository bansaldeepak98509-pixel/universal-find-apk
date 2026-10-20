package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.accessibility.UniversalFindAccessibilityService
import com.example.ocr.OcrResult
import com.example.ocr.OcrTextProvider
import com.example.search.SearchEngine
import com.example.search.SearchQuery
import com.example.search.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingSearchService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingBubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var searchBarView: View? = null
    private var searchBarParams: WindowManager.LayoutParams? = null

    private var highlightOverlayView: HighlightCanvasView? = null
    private var highlightOverlayParams: WindowManager.LayoutParams? = null

    private var isSearchOverlayExpanded = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ocrProvider by lazy { OcrTextProvider() }
    private val searchEngine = SearchEngine()

    private var cachedOcrResult: OcrResult? = null
    private var currentSearchResult = SearchResult(query = SearchQuery(text = ""))
    private var currentQueryText = ""

    private var counterTextView: TextView? = null
    private var queryEditText: EditText? = null

    companion object {
        const val CHANNEL_ID = "floating_search_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_FLOATING_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_FLOATING_SERVICE"

        var isServiceRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingSearchService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isServiceRunning = true
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingSearchService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            isServiceRunning = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(NOTIFICATION_ID, createNotification(), fgsType)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        createFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            collapseSearchOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        isServiceRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        collapseSearchOverlay()

        floatingBubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }
    }

    private fun createFloatingBubble() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 250
        }

        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            val bubbleBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1A73E8")) // Google Blue
                setSize(dpToPx(56), dpToPx(56))
            }
            background = bubbleBg
            setColorFilter(Color.WHITE)
            val pad = dpToPx(14)
            setPadding(pad, pad, pad, pad)
            elevation = dpToPx(8).toFloat()
            contentDescription = "Universal Find Search Bubble"
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        imageView.setOnTouchListener { view, event ->
            val p = bubbleParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isClick = false
                    }
                    p.x = initialX + deltaX
                    p.y = initialY + deltaY
                    windowManager.updateViewLayout(view, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        // FIX: Do NOT open MainActivity via Intent.
                        // Directly expand / toggle the WindowManager search UI overlay!
                        toggleSearchOverlay()
                    }
                    true
                }
                else -> false
            }
        }

        floatingBubbleView = imageView
        try {
            windowManager.addView(imageView, bubbleParams)
        } catch (_: Exception) {}
    }

    private fun toggleSearchOverlay() {
        if (isSearchOverlayExpanded) {
            collapseSearchOverlay()
        } else {
            expandSearchOverlay()
        }
    }

    private fun expandSearchOverlay() {
        if (isSearchOverlayExpanded) return
        isSearchOverlayExpanded = true

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Highlight Overlay View (fullscreen transparent canvas for yellow search match boxes)
        if (highlightOverlayView == null) {
            val canvasView = HighlightCanvasView(this)
            val hParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            highlightOverlayView = canvasView
            highlightOverlayParams = hParams
            try {
                windowManager.addView(canvasView, hParams)
            } catch (_: Exception) {}
        } else {
            highlightOverlayView?.visibility = View.VISIBLE
        }

        // 2. Search Bar View (Chrome-style floating Find in Page toolbar)
        if (searchBarView == null) {
            val barView = createSearchBarView()
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            val barWidth = (metrics.widthPixels * 0.94f).toInt().coerceAtMost(dpToPx(480))

            val sParams = WindowManager.LayoutParams(
                barWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(48)
            }
            searchBarView = barView
            searchBarParams = sParams
            try {
                windowManager.addView(barView, sParams)
            } catch (_: Exception) {}
        } else {
            searchBarView?.visibility = View.VISIBLE
        }

        // Immediately trigger background screen OCR or Accessibility capture
        scanScreenForMatches()

        // Focus search input
        queryEditText?.requestFocus()
        queryEditText?.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            queryEditText?.let { imm?.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
        }
    }

    private fun collapseSearchOverlay() {
        if (!isSearchOverlayExpanded) return
        isSearchOverlayExpanded = false

        searchBarView?.visibility = View.GONE
        highlightOverlayView?.visibility = View.GONE
        highlightOverlayView?.searchResult = SearchResult(query = SearchQuery(text = ""))

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        queryEditText?.let { imm?.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun createSearchBarView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = dpToPx(14)
            val padV = dpToPx(8)
            setPadding(padH, padV, padH, padV)

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28).toFloat()
                setColor(Color.WHITE)
                setStroke(dpToPx(1), Color.parseColor("#DADCE0"))
            }
            background = bg
            elevation = dpToPx(12).toFloat()
        }

        // Search Icon
        val searchIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(Color.parseColor("#1A73E8"))
            val iconSize = dpToPx(22)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = dpToPx(8)
            }
        }
        root.addView(searchIcon)

        // Query EditText
        val editText = EditText(this).apply {
            hint = "Find on screen…"
            setHintTextColor(Color.parseColor("#9AA0A6"))
            setTextColor(Color.parseColor("#202124"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            isSingleLine = true
            background = null
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    currentQueryText = s?.toString() ?: ""
                    applySearchQuery(currentQueryText)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        queryEditText = editText
        root.addView(editText)

        // Match Counter Badge (e.g. "1/5")
        val counter = TextView(this).apply {
            text = "0/0"
            setTextColor(Color.parseColor("#5F6368"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val cPadH = dpToPx(6)
            setPadding(cPadH, 0, cPadH, 0)
        }
        counterTextView = counter
        root.addView(counter)

        // Prev Match Button (▲)
        val prevBtn = createToolbarButton("▲") {
            navigateMatch(forward = false)
        }
        root.addView(prevBtn)

        // Next Match Button (▼)
        val nextBtn = createToolbarButton("▼") {
            navigateMatch(forward = true)
        }
        root.addView(nextBtn)

        // Scan/Refresh Button (↻)
        val refreshBtn = createToolbarButton("↻") {
            scanScreenForMatches()
        }
        root.addView(refreshBtn)

        // Close Button (✕)
        val closeBtn = createToolbarButton("✕") {
            collapseSearchOverlay()
        }
        root.addView(closeBtn)

        return root
    }

    private fun createToolbarButton(symbol: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = symbol
            textSize = 15f
            setTextColor(Color.parseColor("#444746"))
            gravity = Gravity.CENTER
            val p = dpToPx(8)
            setPadding(p, p, p, p)
            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
            }
            background = btnBg
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    private fun scanScreenForMatches() {
        serviceScope.launch {
            counterTextView?.text = "Scanning…"

            // 1. Try Foreground MediaProjection Service capture first
            if (MediaProjectionService.isProjectionActive) {
                try {
                    val bitmap = MediaProjectionService.captureCurrentScreen()
                    if (bitmap != null) {
                        val ocr = withContext(Dispatchers.Default) {
                            ocrProvider.recognizeText(bitmap)
                        }
                        cachedOcrResult = ocr
                        applySearchQuery(currentQueryText)
                        return@launch
                    }
                } catch (_: Exception) {}
            }

            // 2. Try Accessibility Service if connected
            val accessibilityService = UniversalFindAccessibilityService.instance
            if (accessibilityService != null) {
                val query = SearchQuery(text = currentQueryText)
                val result = accessibilityService.searchAccessibilityTree(query)
                currentSearchResult = result
                highlightOverlayView?.searchResult = result
                counterTextView?.text = result.displayCounter
                return@launch
            }

            // Neither is active
            counterTextView?.text = if (currentQueryText.isBlank()) "0/0" else "Start Capture"
        }
    }

    private fun applySearchQuery(queryText: String) {
        val query = SearchQuery(text = queryText)
        if (query.isEmpty) {
            currentSearchResult = SearchResult(query = query)
            highlightOverlayView?.searchResult = currentSearchResult
            counterTextView?.text = "0/0"
            return
        }

        val ocr = cachedOcrResult
        if (ocr != null) {
            val result = ocrProvider.findMatchesInOcr(ocr, query)
            currentSearchResult = result
            highlightOverlayView?.searchResult = result
            counterTextView?.text = result.displayCounter
            return
        }

        val accessibilityService = UniversalFindAccessibilityService.instance
        if (accessibilityService != null) {
            val result = accessibilityService.searchAccessibilityTree(query)
            currentSearchResult = result
            highlightOverlayView?.searchResult = result
            counterTextView?.text = result.displayCounter
            return
        }

        counterTextView?.text = "0/0"
    }

    private fun navigateMatch(forward: Boolean) {
        val matches = currentSearchResult.matches
        if (matches.isEmpty()) return

        val total = matches.size
        val newIndex = if (forward) {
            (currentSearchResult.activeIndex + 1) % total
        } else {
            (currentSearchResult.activeIndex - 1 + total) % total
        }

        currentSearchResult = currentSearchResult.copy(activeIndex = newIndex)
        highlightOverlayView?.searchResult = currentSearchResult
        counterTextView?.text = currentSearchResult.displayCounter
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Universal Find floating overlay widget"
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
            .setContentTitle(getString(R.string.floating_service_notification_title))
            .setContentText(getString(R.string.floating_service_notification_text))
            .setSmallIcon(R.drawable.ic_search)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

/**
 * Dedicated transparent full-screen Canvas view for drawing yellow/amber
 * Chrome-style match highlights directly on top of third-party apps in WindowManager.
 */
class HighlightCanvasView(context: Context) : View(context) {

    var searchResult: SearchResult = SearchResult(query = SearchQuery(text = ""))
        set(value) {
            field = value
            invalidate()
        }

    private val inactiveFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFF176.toInt() // Soft translucent yellow
        style = Paint.Style.FILL
    }

    private val inactiveStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFBC02D.toInt() // Golden yellow border
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val activeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDDFFB74D.toInt() // Vibrant amber fill
        style = Paint.Style.FILL
    }

    private val activeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt() // Glowing orange border
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFBC02D.toInt()
        style = Paint.Style.FILL
    }

    private val activeTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt()
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val matches = searchResult.matches
        if (matches.isEmpty()) return

        val activeIdx = searchResult.activeIndex

        // 1. Draw inactive matches
        for ((idx, match) in matches.withIndex()) {
            if (idx == activeIdx) continue
            val rect = match.rect ?: continue
            val r = RectF(rect.left, rect.top, rect.right, rect.bottom)
            canvas.drawRoundRect(r, 8f, 8f, inactiveFillPaint)
            canvas.drawRoundRect(r, 8f, 8f, inactiveStrokePaint)
        }

        // 2. Draw active match
        if (activeIdx in matches.indices) {
            val active = matches[activeIdx]
            val rect = active.rect
            if (rect != null) {
                val r = RectF(rect.left, rect.top, rect.right, rect.bottom)
                canvas.drawRoundRect(r, 8f, 8f, activeFillPaint)
                canvas.drawRoundRect(r, 8f, 8f, activeStrokePaint)
            }
        }

        // 3. Mini-map ticks on right margin
        val tickWidth = 12f
        val tickX = width.toFloat() - tickWidth
        val screenHeight = height.toFloat().coerceAtLeast(1f)

        for ((idx, match) in matches.withIndex()) {
            val rect = match.rect ?: continue
            val normY = (rect.top / screenHeight).coerceIn(0f, 1f)
            val y = normY * screenHeight
            val isCurrent = (idx == activeIdx)
            val tickHeight = if (isCurrent) 22f else 8f
            val p = if (isCurrent) activeTickPaint else tickPaint
            canvas.drawRect(tickX, y, width.toFloat(), y + tickHeight, p)
        }
    }
}
