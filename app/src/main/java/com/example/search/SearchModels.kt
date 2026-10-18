package com.example.search

import android.graphics.RectF

data class SearchQuery(
    val text: String,
    val matchCase: Boolean = false,
    val wholeWord: Boolean = false,
    val useRegex: Boolean = false
) {
    val isEmpty: Boolean get() = text.trim().isEmpty()
}

data class MatchRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun toAndroidRectF(): RectF = RectF(left, top, right, bottom)

    companion object {
        fun fromAndroidRect(rect: android.graphics.Rect): MatchRect {
            return MatchRect(
                left = rect.left.toFloat(),
                top = rect.top.toFloat(),
                right = rect.right.toFloat(),
                bottom = rect.bottom.toFloat()
            )
        }
    }
}

data class TextMatch(
    val id: Int,
    val start: Int,
    val end: Int,
    val matchedText: String,
    val rect: MatchRect? = null,
    val page: Int = 1,
    val snippet: String = ""
)

data class SearchResult(
    val query: SearchQuery,
    val matches: List<TextMatch> = emptyList(),
    val activeIndex: Int = -1, // 0-based index
    val errorMessage: String? = null
) {
    val totalMatches: Int get() = matches.size
    val activeMatch: TextMatch? get() = if (activeIndex in matches.indices) matches[activeIndex] else null
    val displayCounter: String get() = when {
        errorMessage != null -> "Error"
        query.isEmpty -> ""
        totalMatches == 0 -> "0/0"
        else -> "${activeIndex + 1}/$totalMatches"
    }
}
