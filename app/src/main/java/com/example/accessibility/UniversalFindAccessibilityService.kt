package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.search.MatchRect
import com.example.search.SearchEngine
import com.example.search.SearchQuery
import com.example.search.SearchResult
import com.example.search.TextMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccessibleNodeItem(
    val text: String,
    val bounds: MatchRect,
    val className: String
)

class UniversalFindAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We do not continuously record background events to maintain 100% privacy and zero battery drain.
    }

    override fun onInterrupt() {
        activeServiceInstance = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeServiceInstance = this
        _isServiceConnected.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
        _isServiceConnected.value = false
    }

    /**
     * Traverses the active window's accessibility node tree to gather visible text nodes.
     */
    fun extractScreenNodes(): List<AccessibleNodeItem> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<AccessibleNodeItem>()
        traverseNode(root, results)
        return results
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, list: MutableList<AccessibleNodeItem>) {
        if (node == null) return

        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                list.add(
                    AccessibleNodeItem(
                        text = text,
                        bounds = MatchRect.fromAndroidRect(rect),
                        className = node.className?.toString() ?: ""
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            traverseNode(node.getChild(i), list)
        }
    }

    companion object {
        var activeServiceInstance: UniversalFindAccessibilityService? = null
            private set

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected = _isServiceConnected.asStateFlow()
    }
}

class AccessibleTextProvider(
    private val searchEngine: SearchEngine = SearchEngine()
) {
    fun isAvailable(): Boolean {
        return UniversalFindAccessibilityService.activeServiceInstance != null
    }

    fun searchActiveScreen(query: SearchQuery, activeIndex: Int = 0): SearchResult {
        val service = UniversalFindAccessibilityService.activeServiceInstance
            ?: return SearchResult(
                query = query,
                matches = emptyList(),
                activeIndex = -1,
                errorMessage = "Accessibility Service is not enabled."
            )

        val nodes = service.extractScreenNodes()
        if (nodes.isEmpty() || query.isEmpty) {
            return SearchResult(query = query, matches = emptyList(), activeIndex = -1)
        }

        val allMatches = mutableListOf<TextMatch>()
        var matchId = 0
        val target = if (query.matchCase) query.text else query.text.lowercase()

        for (node in nodes) {
            val nodeText = if (query.matchCase) node.text else node.text.lowercase()
            var searchStart = 0

            while (searchStart < nodeText.length) {
                val foundIdx = nodeText.indexOf(target, searchStart)
                if (foundIdx < 0) break

                val matchEnd = foundIdx + target.length
                val matchedSnippet = node.text.substring(foundIdx, matchEnd)

                allMatches.add(
                    TextMatch(
                        id = matchId++,
                        start = foundIdx,
                        end = matchEnd,
                        matchedText = matchedSnippet,
                        rect = node.bounds,
                        snippet = node.text
                    )
                )

                searchStart = foundIdx + 1
            }
        }

        val resolvedIdx = when {
            allMatches.isEmpty() -> -1
            activeIndex in allMatches.indices -> activeIndex
            else -> 0
        }

        return SearchResult(
            query = query,
            matches = allMatches,
            activeIndex = resolvedIdx
        )
    }
}
