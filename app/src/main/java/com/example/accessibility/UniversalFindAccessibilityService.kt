package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.search.MatchRect
import com.example.search.SearchEngine
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.search.TextMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccessibilityNodeMatch(
    val nodeText: String,
    val bounds: MatchRect,
    val viewId: String? = null
)

class UniversalFindAccessibilityService : AccessibilityService() {

    companion object {
        var instance: UniversalFindAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnectedFlow: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        val isServiceConnected: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener; nodes are queried on-demand during search
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceConnected.value = false
    }

    fun extractAllVisibleTextNodes(
        excludePackage: String? = null,
        excludeRect: RectF? = null
    ): List<AccessibilityNodeMatch> {
        val results = mutableListOf<AccessibilityNodeMatch>()

        // Check all interactive on-screen windows (includes background apps like WhatsApp, browsers)
        val allWindows = try {
            windows
        } catch (_: Exception) {
            null
        }

        if (!allWindows.isNullOrEmpty()) {
            for (window in allWindows) {
                val root = window.root ?: continue
                traverseNode(root, results, excludePackage, excludeRect)
            }
        } else {
            rootInActiveWindow?.let { traverseNode(it, results, excludePackage, excludeRect) }
        }
        return results
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        results: MutableList<AccessibilityNodeMatch>,
        excludePackage: String? = null,
        excludeRect: RectF? = null
    ) {
        if (node == null) return

        val pkg = node.packageName?.toString()
        // 1. Exclude the app's own package so we never scan our own floating search UI
        if (excludePackage != null && pkg == excludePackage) {
            return
        }

        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank() && node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                val nodeRectF = RectF(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat()
                )
                // 2. Exclude nodes that fall within the overlay search bar region
                val isInsideExclude = excludeRect != null && RectF.intersects(excludeRect, nodeRectF)
                if (!isInsideExclude) {
                    results.add(
                        AccessibilityNodeMatch(
                            nodeText = text,
                            bounds = MatchRect(
                                left = bounds.left.toFloat(),
                                top = bounds.top.toFloat(),
                                right = bounds.right.toFloat(),
                                bottom = bounds.bottom.toFloat()
                            ),
                            viewId = node.viewIdResourceName
                        )
                    )
                }
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), results, excludePackage, excludeRect)
        }
    }

    private val searchEngine = SearchEngine()

    fun searchAccessibilityTree(
        query: SearchQuery,
        excludePackage: String? = null,
        excludeRect: RectF? = null
    ): SearchResult {
        if (query.isEmpty) {
            return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
        }

        val nodes = extractAllVisibleTextNodes(excludePackage, excludeRect)
        val matches = mutableListOf<TextMatch>()
        var matchId = 0

        for (node in nodes) {
            val engineResult = searchEngine.search(node.nodeText, query)
            if (engineResult.errorMessage != null && matches.isEmpty()) {
                return engineResult
            }

            val lines = node.nodeText.split("\n")
            val totalLines = lines.size.coerceAtLeast(1)
            val lineHeight = node.bounds.height / totalLines

            for (m in engineResult.matches) {
                // Compute accurate bounding box within the node
                var charCount = 0
                var lineIndex = 0
                var lineStartChar = 0

                for (lIdx in lines.indices) {
                    val lineLen = lines[lIdx].length + 1 // accounts for '\n'
                    if (charCount + lineLen > m.start) {
                        lineIndex = lIdx
                        lineStartChar = charCount
                        break
                    }
                    charCount += lineLen
                }

                val lineText = lines[lineIndex]
                val matchStartInLine = (m.start - lineStartChar).coerceIn(0, lineText.length)
                val matchEndInLine = (m.end - lineStartChar).coerceIn(matchStartInLine, lineText.length)

                val fractionStart = if (lineText.isNotEmpty()) matchStartInLine.toFloat() / lineText.length else 0f
                val fractionEnd = if (lineText.isNotEmpty()) matchEndInLine.toFloat() / lineText.length else 1f

                val nodeWidth = node.bounds.width
                val matchLeft = node.bounds.left + (nodeWidth * fractionStart)
                val matchRight = node.bounds.left + (nodeWidth * fractionEnd)
                val matchTop = node.bounds.top + (lineIndex * lineHeight)
                val matchBottom = matchTop + lineHeight

                val accurateRect = MatchRect(
                    left = matchLeft,
                    top = matchTop,
                    right = matchRight,
                    bottom = matchBottom
                )

                matches.add(
                    m.copy(
                        id = matchId++,
                        rect = accurateRect,
                        snippet = node.nodeText
                    )
                )
            }
        }

        return SearchResult(
            query = query,
            matches = matches,
            activeIndex = if (matches.isNotEmpty()) 0 else -1
        )
    }

    /**
     * Auto-Scroll for Hidden Words:
     * Detects if target match bounding box is positioned below the visible screen area
     * (or under keyboard / floating search bar), and automatically injects a scroll gesture
     * or ACTION_SCROLL_FORWARD / BACKWARD to bring it into the viewport.
     */
    fun scrollToMakeVisible(
        matchRect: MatchRect,
        screenHeight: Int,
        screenWidth: Int,
        keyboardHeight: Int = 0
    ): Boolean {
        val visibleTop = 160f // Search overlay height + safety margin
        val visibleBottom = (screenHeight - keyboardHeight - 120).toFloat()

        val isBelow = matchRect.bottom > visibleBottom || matchRect.top > visibleBottom - 60f
        val isAbove = matchRect.top < visibleTop

        if (!isBelow && !isAbove) {
            // Already visible in current viewport
            return false
        }

        // 1. Try ACTION_SCROLL_FORWARD / BACKWARD on scrollable node in active window
        val scrolledViaAction = scrollScrollableContainer(forward = isBelow)
        if (scrolledViaAction) {
            return true
        }

        // 2. Fallback to injected gesture swipe via dispatchGesture
        return performScrollGesture(scrollDown = isBelow, screenWidth = screenWidth, screenHeight = screenHeight)
    }

    private fun scrollScrollableContainer(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollableNode = findScrollableNode(root)
        if (scrollableNode != null) {
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            return scrollableNode.performAction(action)
        }
        return false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val scrollable = findScrollableNode(child)
            if (scrollable != null) {
                return scrollable
            }
        }
        return null
    }

    private fun performScrollGesture(
        scrollDown: Boolean,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val startX = screenWidth / 2f
        val startY = if (scrollDown) screenHeight * 0.70f else screenHeight * 0.30f
        val endY = if (scrollDown) screenHeight * 0.30f else screenHeight * 0.70f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }
}
