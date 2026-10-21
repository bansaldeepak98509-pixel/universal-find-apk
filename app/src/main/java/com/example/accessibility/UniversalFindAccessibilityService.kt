package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.search.MatchRect
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
        val target = if (query.matchCase) query.text else query.text.lowercase()

        for (node in nodes) {
            val textNorm = if (query.matchCase) node.nodeText else node.nodeText.lowercase()
            var start = 0
            while (start < textNorm.length) {
                val found = textNorm.indexOf(target, start)
                if (found < 0) break

                val end = found + target.length
                matches.add(
                    TextMatch(
                        id = matchId++,
                        start = found,
                        end = end,
                        matchedText = node.nodeText.substring(found, end),
                        rect = node.bounds,
                        snippet = node.nodeText
                    )
                )
                start = found + 1
            }
        }

        return SearchResult(
            query = query,
            matches = matches,
            activeIndex = if (matches.isNotEmpty()) 0 else -1
        )
    }
}
