package com.example.prismlock

import com.example.prismlock.engine.TouchActionExecutor
import com.example.prismlock.model.Project
import com.example.prismlock.model.WIDGET_ASPECT_MAX
import com.example.prismlock.model.WIDGET_ASPECT_MIN
import com.example.prismlock.model.migrated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrismStage6Test {
    @Test
    fun openUrlAllowsOnlySafeSchemes() {
        assertTrue(TouchActionExecutor.isAllowedUrlScheme("https://example.com"))
        assertTrue(TouchActionExecutor.isAllowedUrlScheme("http://example.com"))
        assertTrue(TouchActionExecutor.isAllowedUrlScheme("mailto:user@example.com"))
        assertTrue(TouchActionExecutor.isAllowedUrlScheme("tel:+5511999999999"))

        assertFalse(TouchActionExecutor.isAllowedUrlScheme("intent://scan/#Intent;end"))
        assertFalse(TouchActionExecutor.isAllowedUrlScheme("file:///etc/hosts"))
        assertFalse(TouchActionExecutor.isAllowedUrlScheme("content://com.example/secret"))
        assertFalse(TouchActionExecutor.isAllowedUrlScheme("javascript:alert(1)"))
    }

    @Test
    fun legacyPhantomSaoPauloCoordinatesAreClearedOnMigration() {
        val legacy = Project(city = "", lat = -23.55, lon = -46.63).migrated()
        assertNull(legacy.lat)
        assertNull(legacy.lon)

        val explicitSp = Project(city = "São Paulo", lat = -23.55, lon = -46.63).migrated()
        assertEquals(-23.55, explicitSp.lat!!, 0.001)
        assertEquals(-46.63, explicitSp.lon!!, 0.001)
    }

    @Test
    fun widgetAspectBoundsAreConsistent() {
        assertEquals(0.6f, WIDGET_ASPECT_MIN, 0.0001f)
        assertEquals(4.5f, WIDGET_ASPECT_MAX, 0.0001f)
    }
}
