package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import java.util.ArrayList
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.search.MatchRect
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.search.TextMatch

data class AccessibilityNodeMatch(
    val nodeText: String,
    val bounds: MatchRect,
    val viewId: String? = null
)

class UniversalFindAccessibilityService : AccessibilityService() {

    companion object {
        var instance: UniversalFindAccessibilityService? = null
            private set

        val isServiceConnected: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener; nodes are queried on-demand during search
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun extractAllVisibleTextNodes(): List<AccessibilityNodeMatch> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeMatch>()
        traverseNode(root, results)
        return results
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, results: MutableList<AccessibilityNodeMatch>) {
        if (node == null) return

        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank() && node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
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

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), results)
        }
    }

    fun searchAccessibilityTree(query: SearchQuery): SearchResult {
        if (query.isEmpty) {
            return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
        }

        val nodes = extractAllVisibleTextNodes()
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
