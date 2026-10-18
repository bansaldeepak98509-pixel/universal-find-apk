package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
                color = Color(0xFFFBBF24).copy(alpha = 0.8f),
                topLeft = Offset(mapped.left, mapped.top),
                size = Size(mapped.width(), mapped.height()),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.5f)
            )
        }

        // 2. Draw Active Match (Vibrant Orange with bold border)
        searchResult.activeMatch?.rect?.let { activeRect ->
            val mapped = MatchCoordinateMapper.mapToContainer(activeRect, transform)

            drawRoundRect(
                color = HighlightActiveColor.copy(alpha = 0.75f),
                topLeft = Offset(mapped.left, mapped.top),
                size = Size(mapped.width(), mapped.height()),
                cornerRadius = CornerRadius(4f, 4f)
            )

            drawRoundRect(
                color = HighlightActiveBorder,
                topLeft = Offset(mapped.left - 1f, mapped.top - 1f),
                size = Size(mapped.width() + 2f, mapped.height() + 2f),
                cornerRadius = CornerRadius(5f, 5f),
                style = Stroke(width = 2.5f)
            )
        }

        // 3. Chrome-style Vertical Mini-Map Tick Marks (on right edge of viewport)
        val gutterWidth = 6f
        val gutterX = size.width - gutterWidth - 2f

        for ((index, match) in searchResult.matches.withIndex()) {
            val rect = match.rect ?: continue
            // Relative vertical position (0.0 to 1.0)
            val relY = (rect.top / sourceHeight).coerceIn(0f, 1f)
            val tickY = relY * size.height

            val isSelected = index == searchResult.activeIndex
            val tickColor = if (isSelected) HighlightActiveColor else HighlightInactiveColor
            val tickHeight = if (isSelected) 8f else 4f

            drawRect(
                color = tickColor,
                topLeft = Offset(gutterX, (tickY - tickHeight / 2).coerceIn(0f, size.height - tickHeight)),
                size = Size(gutterWidth, tickHeight)
            )
        }
    }
}
