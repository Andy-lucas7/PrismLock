package com.example.prismlock

import com.example.prismlock.wallpaper.MotionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class MotionFilterTest {
    private fun deg(d: Float): Float = (d * PI / 180.0).toFloat()

    @Test
    fun singleFrame40DegSpikeIsClampedByVelocityLimit() {
        val filter = MotionFilter()
        filter.update(0f, 0f, 1_000_000_000L)
        val (x, y) = filter.update(deg(40f), deg(-40f), 1_016_000_000L)
        assertTrue("Expected |x| <= 0.25, got $x", abs(x) <= 0.2501f)
        assertTrue("Expected |y| <= 0.25, got $y", abs(y) <= 0.2501f)
    }

    @Test
    fun holding70DegFor3SecondsAutoRecentersNearZero() {
        val filter = MotionFilter()
        var ts = 1_000_000_000L
        filter.update(0f, 0f, ts)
        var lastX = 0f
        // Step at 60 Hz for 8 seconds held steady at 70 deg (tau = 1.5s)
        for (i in 1..480) {
            ts += 16_666_667L
            lastX = filter.update(deg(70f), 0f, ts).first
        }
        assertTrue("Expected recentered output near 0, got $lastX", abs(lastX) < 0.08f)
    }

    @Test
    fun noiseWithinDeadZoneProducesZero() {
        val filter = MotionFilter()
        var ts = 1_000_000_000L
        filter.update(0f, 0f, ts)
        for (i in 1..20) {
            ts += 16_000_000L
            val jitter = if (i % 2 == 0) deg(0.4f) else deg(-0.4f)
            val (x, y) = filter.update(jitter, -jitter, ts)
            assertEquals(0f, x, 0.0001f)
            assertEquals(0f, y, 0.0001f)
        }
    }

    @Test
    fun timestampGapOver100MsResetsWithoutJumping() {
        val filter = MotionFilter()
        filter.update(0f, 0f, 1_000_000_000L)
        filter.update(deg(10f), deg(10f), 1_016_000_000L)
        // 300ms gap with large orientation change -> must reset cleanly to 0
        val (x, y) = filter.update(deg(60f), deg(-45f), 1_316_000_000L)
        assertEquals(0f, x, 0.0001f)
        assertEquals(0f, y, 0.0001f)
    }

    @Test
    fun tanhSaturationNeverExceedsUnitMagnitude() {
        val filter = MotionFilter(maxStepPerFrame = 1.0f)
        var ts = 1_000_000_000L
        filter.update(0f, 0f, ts)
        for (i in 1..15) {
            ts += 16_000_000L
            val (x, y) = filter.update(deg(180f), deg(-180f), ts)
            assertTrue("x exceeded 1.0: $x", abs(x) <= 1.0f)
            assertTrue("y exceeded 1.0: $y", abs(y) <= 1.0f)
        }
        val midFilter = MotionFilter(maxStepPerFrame = 1.0f)
        midFilter.update(0f, 0f, 1_000_000_000L)
        val (mx, my) = midFilter.update(deg(24f), deg(-24f), 1_016_000_000L)
        assertTrue("Expected soft saturation < 1.0, got $mx", abs(mx) > 0f && abs(mx) < 1.0f)
        assertTrue("Expected soft saturation < 1.0, got $my", abs(my) > 0f && abs(my) < 1.0f)
    }
}
