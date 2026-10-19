package com.example.overlay

import android.graphics.PointF
import android.graphics.RectF
import com.example.search.MatchRect

object MatchCoordinateMapper {

    data class ViewportTransform(
        val scale: Float = 1.0f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val rotationDegrees: Int = 0,
        val sourceWidth: Float = 0f,
        val sourceHeight: Float = 0f
    )

    fun calculateFitTransform(
        sourceWidth: Float,
        sourceHeight: Float,
        containerWidth: Float,
        containerHeight: Float,
        rotationDegrees: Int = 0
    ): ViewportTransform {
        if (sourceWidth <= 0 || sourceHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            return ViewportTransform()
        }

        val normRotation = ((rotationDegrees % 360) + 360) % 360
        val isSwapped = normRotation == 90 || normRotation == 270

        val effectiveWidth = if (isSwapped) sourceHeight else sourceWidth
        val effectiveHeight = if (isSwapped) sourceWidth else sourceHeight

        val scale = minOf(containerWidth / effectiveWidth, containerHeight / effectiveHeight)
        val drawnWidth = effectiveWidth * scale
        val drawnHeight = effectiveHeight * scale

        val offsetX = (containerWidth - drawnWidth) / 2f
        val offsetY = (containerHeight - drawnHeight) / 2f

        return ViewportTransform(
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            rotationDegrees = normRotation,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight
        )
    }

    fun mapToContainer(rect: MatchRect, transform: ViewportTransform): RectF {
        val sW = transform.sourceWidth
        val sH = transform.sourceHeight

        val (rotLeft, rotTop, rotRight, rotBottom) = when (transform.rotationDegrees) {
            90 -> {
                val newL = sH - rect.bottom
                val newT = rect.left
                val newR = sH - rect.top
                val newB = rect.right
                listOf(newL, newT, newR, newB)
            }
            180 -> {
                val newL = sW - rect.right
                val newT = sH - rect.bottom
                val newR = sW - rect.left
                val newB = sH - rect.top
                listOf(newL, newT, newR, newB)
            }
            270 -> {
                val newL = rect.top
                val newT = sW - rect.right
                val newR = rect.bottom
                val newB = sW - rect.left
                listOf(newL, newT, newR, newB)
            }
            else -> {
                listOf(rect.left, rect.top, rect.right, rect.bottom)
            }
        }

        val left = (rotLeft * transform.scale) + transform.offsetX
        val top = (rotTop * transform.scale) + transform.offsetY
        val right = (rotRight * transform.scale) + transform.offsetX
        val bottom = (rotBottom * transform.scale) + transform.offsetY

        return RectF(
            minOf(left, right),
            minOf(top, bottom),
            maxOf(left, right),
            maxOf(top, bottom)
        )
    }

    fun mapFromContainer(point: PointF, transform: ViewportTransform): PointF {
        if (transform.scale <= 0f) return PointF(0f, 0f)

        val unscaledX = (point.x - transform.offsetX) / transform.scale
        val unscaledY = (point.y - transform.offsetY) / transform.scale

        val sW = transform.sourceWidth
        val sH = transform.sourceHeight

        return when (transform.rotationDegrees) {
            90 -> PointF(unscaledY, sH - unscaledX)
            180 -> PointF(sW - unscaledX, sH - unscaledY)
            270 -> PointF(sW - unscaledY, unscaledX)
            else -> PointF(unscaledX, unscaledY)
        }
    }
}
