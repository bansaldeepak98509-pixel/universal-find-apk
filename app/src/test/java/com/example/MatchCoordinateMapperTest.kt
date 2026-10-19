package com.example

import com.example.overlay.MatchCoordinateMapper
import com.example.search.MatchRect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MatchCoordinateMapperTest {

    @Test
    fun `calculateFitTransform maintains aspect ratio`() {
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

        assertEquals(20f, mapped.left, 0.001f)
        assertEquals(40f, mapped.top, 0.001f)
        assertEquals(60f, mapped.right, 0.001f)
        assertEquals(80f, mapped.bottom, 0.001f)
    }

    @Test
    fun `mapToContainer rotates 90 degrees clockwise accurately`() {
        val transform = MatchCoordinateMapper.calculateFitTransform(
            sourceWidth = 100f,
            sourceHeight = 200f,
            containerWidth = 200f,
            containerHeight = 100f,
            rotationDegrees = 90
        )
        assertEquals(1.0f, transform.scale, 0.001f)

        val rect = MatchRect(left = 10f, top = 20f, right = 30f, bottom = 40f)
        val mapped = MatchCoordinateMapper.mapToContainer(rect, transform)

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

        assertEquals(100f, unmapped.x, 0.001f)
        assertEquals(200f, unmapped.y, 0.001f)
    }
}
