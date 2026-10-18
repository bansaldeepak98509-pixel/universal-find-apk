package com.example

import com.example.overlay.MatchCoordinateMapper
import com.example.search.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MatchCoordinateMapperTest {

    @Test
    fun `calculateFitTransform maintains aspect ratio`() {
        // 1000x2000 into 500x1000 -> scale 0.5f, no offset
        val transform = MatchCoordinateMapper.calculateFitTransform(
            sourceWidth = 1000f,
            sourceHeight = 2000f,
            containerWidth = 500f,
            containerHeight = 1000f
        )
        assertEquals(0.5f, transform.scale, 0.001f)
        assertEquals(0f, transform.offsetX, 0.001f)
        assertEquals(0f, transform.offsetY, 0.001f)
    }

    @Test
    fun `calculateFitTransform centers with letterboxing`() {
        // 1000x1000 into 1000x2000 -> scale 1.0f, letterboxed vertically by 500px
        val transform = MatchCoordinateMapper.calculateFitTransform(
            sourceWidth = 1000f,
            sourceHeight = 1000f,
            containerWidth = 1000f,
            containerHeight = 2000f
        )
        assertEquals(1.0f, transform.scale, 0.001f)
        assertEquals(0f, transform.offsetX, 0.001f)
        assertEquals(500f, transform.offsetY, 0.001f)
    }

    @Test
    fun `mapToContainer transforms coordinates accurately`() {
        val transform = MatchCoordinateMapper.ViewportTransform(
            scale = 2.0f,
            offsetX = 10f,
            offsetY = 20f
        )
        val rect = MatchRect(left = 5f, top = 10f, right = 25f, bottom = 30f)
        val mapped = MatchCoordinateMapper.mapToContainer(rect, transform)

        // left = 5 * 2 + 10 = 20
        // top = 10 * 2 + 20 = 40
        // right = 25 * 2 + 10 = 60
        // bottom = 30 * 2 + 20 = 80
        assertEquals(20f, mapped.left, 0.001f)
        assertEquals(40f, mapped.top, 0.001f)
        assertEquals(60f, mapped.right, 0.001f)
        assertEquals(80f, mapped.bottom, 0.001f)
    }

    @Test
    fun `mapToContainer rotates 90 degrees clockwise accurately`() {
        // Source 100x200 rotated 90 degrees in 200x100 container -> scale 1.0f
        val transform = MatchCoordinateMapper.calculateFitTransform(
            sourceWidth = 100f,
            sourceHeight = 200f,
            containerWidth = 200f,
            containerHeight = 100f,
            rotationDegrees = 90
        )
        assertEquals(1.0f, transform.scale, 0.001f)

        // Rect at left=10, top=20, right=30, bottom=40
        val rect = MatchRect(left = 10f, top = 20f, right = 30f, bottom = 40f)
        val mapped = MatchCoordinateMapper.mapToContainer(rect, transform)

        // For 90 deg:
        // rotLeft = sH - bottom = 200 - 40 = 160
        // rotTop = left = 10
        // rotRight = sH - top = 200 - 20 = 180
        // rotBottom = right = 30
        assertEquals(160f, mapped.left, 0.001f)
        assertEquals(10f, mapped.top, 0.001f)
        assertEquals(180f, mapped.right, 0.001f)
        assertEquals(30f, mapped.bottom, 0.001f)
    }

    @Test
    fun `mapFromContainer inverts coordinates accurately`() {
        val transform = MatchCoordinateMapper.ViewportTransform(
            scale = 2.0f,
            offsetX = 50f,
            offsetY = 100f,
            rotationDegrees = 0,
            sourceWidth = 500f,
            sourceHeight = 1000f
        )
        val containerPoint = android.graphics.PointF(250f, 500f)
        val unmapped = MatchCoordinateMapper.mapFromContainer(containerPoint, transform)

        // (250 - 50) / 2 = 100
        // (500 - 100) / 2 = 200
        assertEquals(100f, unmapped.x, 0.001f)
        assertEquals(200f, unmapped.y, 0.001f)
    }
}
