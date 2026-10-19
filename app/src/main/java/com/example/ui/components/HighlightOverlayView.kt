package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.overlay.MatchCoordinateMapper
import com.example.search.SearchResult
import com.example.ui.theme.HighlightActiveBorder
import com.example.ui.theme.HighlightActiveColor
import com.example.ui.theme.HighlightInactiveColor

@Composable
fun HighlightOverlayView(
    searchResult: SearchResult,
    sourceWidth: Float,
    sourceHeight: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || searchResult.matches.isEmpty()) return@Canvas

        val transform = MatchCoordinateMapper.calculateFitTransform(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            containerWidth = size.width,
            containerHeight = size.height
        )

        // 1. Draw Inactive Matches (semi-transparent soft yellow)
        for ((index, match) in searchResult.matches.withIndex()) {
            if (index == searchResult.activeIndex) continue
            val rect = match.rect ?: continue
            val mapped = MatchCoordinateMapper.mapToContainer(rect, transform)

            drawRoundRect(
                color = HighlightInactiveColor.copy(alpha = 0.55f),
                topLeft = Offset(mapped.left, mapped.top),
                size = Size(mapped.width(), mapped.height()),
                cornerRadius = CornerRadius(4f, 4f)
            )

            drawRoundRect(
                color = Color(0xFFFBC02D).copy(alpha = 0.8f),
                topLeft = Offset(mapped.left, mapped.top),
                size = Size(mapped.width(), mapped.height()),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.5f)
            )
        }

        // 2. Draw Active Match (vibrant amber fill with thick orange border)
        val activeMatch = searchResult.activeMatch
        if (activeMatch != null && activeMatch.rect != null) {
            val mappedActive = MatchCoordinateMapper.mapToContainer(activeMatch.rect, transform)

            drawRoundRect(
                color = HighlightActiveColor.copy(alpha = 0.85f),
                topLeft = Offset(mappedActive.left, mappedActive.top),
                size = Size(mappedActive.width(), mappedActive.height()),
                cornerRadius = CornerRadius(6f, 6f)
            )

            drawRoundRect(
                color = HighlightActiveBorder,
                topLeft = Offset(mappedActive.left - 2f, mappedActive.top - 2f),
                size = Size(mappedActive.width() + 4f, mappedActive.height() + 4f),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 3.5f)
            )
        }

        // 3. Right-Hand Mini-map Scroll Tick Marks
        val tickBarWidth = 6.dp.toPx()
        val tickX = size.width - tickBarWidth - 2.dp.toPx()

        for ((index, match) in searchResult.matches.withIndex()) {
            val rect = match.rect ?: continue
            val normY = rect.top / sourceHeight.coerceAtLeast(1f)
            val tickY = normY * size.height
            val isCurrent = (index == searchResult.activeIndex)

            drawRect(
                color = if (isCurrent) HighlightActiveBorder else Color(0xFFFBC02D),
                topLeft = Offset(tickX, tickY),
                size = Size(tickBarWidth, if (isCurrent) 10.dp.toPx() else 4.dp.toPx())
            )
        }
    }
}
