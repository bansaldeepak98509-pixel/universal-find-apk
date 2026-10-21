package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
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
import com.example.search.SearchQuery
import com.example.search.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    private var currentSearchResult = SearchResult(query = SearchQuery(text = ""))
    private var currentQueryText = ""
    private var searchDebounceJob: Job? = null

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
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

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        // 1. Highlight Overlay View (fullscreen transparent canvas for background search match boxes)
        if (highlightOverlayView == null) {
            val canvasView = HighlightCanvasView(this)
            val hParams = WindowManager.LayoutParams(
                metrics.widthPixels,
                metrics.heightPixels,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            highlightOverlayView = canvasView
            highlightOverlayParams = hParams
            try {
                windowManager.addView(canvasView, hParams)
            } catch (_: Exception) {}
        } else {
            highlightOverlayView?.visibility = View.VISIBLE
        }

        // 2. Search Bar View (Chrome-style floating Find toolbar)
        if (searchBarView == null) {
            val barView = createSearchBarView()
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

        // If query already entered, trigger fresh scan
        if (currentQueryText.isNotBlank()) {
            onQueryChanged(currentQueryText)
        }

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
                    val query = s?.toString() ?: ""
                    currentQueryText = query
                    // REQUIREMENT 3: Force fresh screen capture & update matches whenever query changes
                    onQueryChanged(query)
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
            serviceScope.launch {
                counterTextView?.text = "Scanning…"
                scanScreenForMatches()
            }
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

    private fun onQueryChanged(query: String) {
        if (query.trim().isEmpty()) {
            currentSearchResult = SearchResult(query = SearchQuery(text = ""))
            highlightOverlayView?.searchResult = currentSearchResult
            counterTextView?.text = "0/0"
            return
        }

        // Cancel previous pending search job for typing responsiveness
        searchDebounceJob?.cancel()
        searchDebounceJob = serviceScope.launch {
            delay(120) // Brief debounce to allow fluid typing without choking frame reader
            counterTextView?.text = "Scanning…"
            scanScreenForMatches()
        }
    }

    private fun getOverlayExcludeRect(): RectF? {
        val bar = searchBarView ?: return null
        val loc = IntArray(2)
        bar.getLocationOnScreen(loc)
        return RectF(
            loc[0].toFloat() - 20f,
            loc[1].toFloat() - 20f,
            (loc[0] + bar.width).toFloat() + 20f,
            (loc[1] + bar.height).toFloat() + 20f
        )
    }

    private fun getBubbleExcludeRect(): RectF? {
        val bubble = floatingBubbleView ?: return null
        val loc = IntArray(2)
        bubble.getLocationOnScreen(loc)
        return RectF(
            loc[0].toFloat() - 20f,
            loc[1].toFloat() - 20f,
            (loc[0] + bubble.width).toFloat() + 20f,
            (loc[1] + bubble.height).toFloat() + 20f
        )
    }

    private suspend fun scanScreenForMatches() {
        val queryText = currentQueryText
        if (queryText.isBlank()) {
            currentSearchResult = SearchResult(query = SearchQuery(text = ""))
            highlightOverlayView?.searchResult = currentSearchResult
            counterTextView?.text = "0/0"
            return
        }

        val barRect = getOverlayExcludeRect()
        val bubbleRect = getBubbleExcludeRect()

        // 1. Try Foreground MediaProjection Service capture first
        if (MediaProjectionService.isProjectionActive) {
            try {
                val bitmap = MediaProjectionService.captureCurrentScreen()
                if (bitmap != null) {
                    val ocr = withContext(Dispatchers.Default) {
                        ocrProvider.recognizeText(bitmap)
                    }

                    // REQUIREMENT 1: Completely filter out any OCR bounding boxes belonging to the overlay search UI
                    val backgroundItems = ocr.items.filterNot { item ->
                        val r = RectF(
                            item.boundingBox.left,
                            item.boundingBox.top,
                            item.boundingBox.right,
                            item.boundingBox.bottom
                        )
                        (barRect != null && RectF.intersects(barRect, r)) ||
                        (bubbleRect != null && RectF.intersects(bubbleRect, r))
                    }

                    val cleanOcr = ocr.copy(items = backgroundItems)
                    val result = ocrProvider.findMatchesInOcr(cleanOcr, SearchQuery(queryText))
                    currentSearchResult = result

                    // REQUIREMENT 2: Accurately map bounding boxes to screen dimensions
                    highlightOverlayView?.updateResults(
                        result = result,
                        sWidth = bitmap.width.toFloat(),
                        sHeight = bitmap.height.toFloat()
                    )
                    counterTextView?.text = result.displayCounter
                    return
                }
            } catch (_: Exception) {}
        }

        // 2. Try Accessibility Service (with windows + app package exclusion)
        val accessibilityService = UniversalFindAccessibilityService.instance
        if (accessibilityService != null) {
            val result = accessibilityService.searchAccessibilityTree(
                query = SearchQuery(queryText),
                excludePackage = packageName,
                excludeRect = barRect
            )
            currentSearchResult = result

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            highlightOverlayView?.updateResults(
                result = result,
                sWidth = metrics.widthPixels.toFloat(),
                sHeight = metrics.heightPixels.toFloat()
            )
            counterTextView?.text = result.displayCounter
            return
        }

        // Neither active
        counterTextView?.text = "0/0"
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        queryEditText?.let {
            it.clearFocus()
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun navigateMatch(forward: Boolean) {
        val matches = currentSearchResult.matches
        if (matches.isEmpty()) return

        // Hide keyboard on arrow navigation clicks so bottom screen is not blocked
        hideKeyboard()

        val total = matches.size
        val newIndex = if (forward) {
            (currentSearchResult.activeIndex + 1) % total
        } else {
            (currentSearchResult.activeIndex - 1 + total) % total
        }

        currentSearchResult = currentSearchResult.copy(activeIndex = newIndex)
        highlightOverlayView?.searchResult = currentSearchResult
        counterTextView?.text = currentSearchResult.displayCounter

        // Auto-Scroll for Hidden Words:
        // When navigating to the next/prev match, if target MatchRect is out of visible bounds
        // or under the keyboard/overlay, scroll it into the viewport automatically.
        val activeMatch = matches[newIndex]
        val rect = activeMatch.rect
        if (rect != null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val accessibilityService = UniversalFindAccessibilityService.instance
            if (accessibilityService != null) {
                val didScroll = accessibilityService.scrollToMakeVisible(
                    matchRect = rect,
                    screenHeight = metrics.heightPixels,
                    screenWidth = metrics.widthPixels
                )
                if (didScroll) {
                    // Trigger fresh background scan after scroll settle so highlights match new viewport
                    serviceScope.launch {
                        delay(400)
                        scanScreenForMatches()
                    }
                }
            }
        }
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
 * Chrome-style match highlights directly over background app text in WindowManager.
 */
class HighlightCanvasView(context: Context) : View(context) {

    var sourceWidth: Float = 0f
    var sourceHeight: Float = 0f

    var searchResult: SearchResult = SearchResult(query = SearchQuery(text = ""))
        set(value) {
            field = value
            invalidate()
        }

    fun updateResults(result: SearchResult, sWidth: Float, sHeight: Float) {
        this.sourceWidth = sWidth
        this.sourceHeight = sHeight
        this.searchResult = result
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

        // Accurately map coordinates to screen canvas dimensions
        val scaleX = if (sourceWidth > 0f && width > 0) width.toFloat() / sourceWidth else 1.0f
        val scaleY = if (sourceHeight > 0f && height > 0) height.toFloat() / sourceHeight else 1.0f

        // 1. Draw inactive matches
        for ((idx, match) in matches.withIndex()) {
            if (idx == activeIdx) continue
            val rect = match.rect ?: continue
            val r = RectF(
                rect.left * scaleX,
                rect.top * scaleY,
                rect.right * scaleX,
                rect.bottom * scaleY
            )
            canvas.drawRoundRect(r, 6f, 6f, inactiveFillPaint)
            canvas.drawRoundRect(r, 6f, 6f, inactiveStrokePaint)
        }

        // 2. Draw active match
        if (activeIdx in matches.indices) {
            val active = matches[activeIdx]
            val rect = active.rect
            if (rect != null) {
                val r = RectF(
                    rect.left * scaleX,
                    rect.top * scaleY,
                    rect.right * scaleX,
                    rect.bottom * scaleY
                )
                canvas.drawRoundRect(r, 8f, 8f, activeFillPaint)
                canvas.drawRoundRect(r, 8f, 8f, activeStrokePaint)
            }
        }

        // 3. Mini-map ticks on right margin
        val tickWidth = 12f
        val tickX = width.toFloat() - tickWidth
        val viewH = height.toFloat().coerceAtLeast(1f)

        for ((idx, match) in matches.withIndex()) {
            val rect = match.rect ?: continue
            val normY = (rect.top * scaleY) / viewH
            val y = (normY * viewH).coerceIn(0f, viewH - 24f)
            val isCurrent = (idx == activeIdx)
            val tickHeight = if (isCurrent) 22f else 8f
            val p = if (isCurrent) activeTickPaint else tickPaint
            canvas.drawRect(tickX, y, width.toFloat(), y + tickHeight, p)
        }
    }
}
