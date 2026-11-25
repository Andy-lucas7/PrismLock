package com.example.prismlock

import com.example.prismlock.ui.PrismMotion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrismMotionTokensTest {
    @Test
    fun prismMotionMatchesCanonicalTokensJson() {
        val candidates = listOf(
            File("docs/motion.tokens.json"),
            File("../docs/motion.tokens.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("docs/motion.tokens.json not found from ${File(".").absolutePath}")
        val root = Json.parseToJsonElement(file.readText()).jsonObject

        val duration = root["duration"]!!.jsonObject
        assertEquals(duration["instant"]!!.jsonPrimitive.int, PrismMotion.Duration.INSTANT)
        assertEquals(duration["fast"]!!.jsonPrimitive.int, PrismMotion.Duration.FAST)
        assertEquals(duration["base"]!!.jsonPrimitive.int, PrismMotion.Duration.BASE)
        assertEquals(duration["slow"]!!.jsonPrimitive.int, PrismMotion.Duration.SLOW)
        assertEquals(duration["hero"]!!.jsonPrimitive.int, PrismMotion.Duration.HERO)
        assertEquals(duration["intro"]!!.jsonPrimitive.int, PrismMotion.Duration.INTRO)

        val spring = root["spring"]!!.jsonObject
        val tactile = spring["tactile"]!!.jsonObject
        assertEquals(tactile["stiffness"]!!.jsonPrimitive.float, PrismMotion.SpringParams.TACTILE_STIFFNESS, 0.001f)
        assertEquals(tactile["damping"]!!.jsonPrimitive.float, PrismMotion.SpringParams.TACTILE_DAMPING, 0.001f)
        assertEquals(tactile["mass"]!!.jsonPrimitive.float, PrismMotion.SpringParams.TACTILE_MASS, 0.001f)

        val carousel = spring["carousel"]!!.jsonObject
        assertEquals(carousel["stiffness"]!!.jsonPrimitive.float, PrismMotion.SpringParams.CAROUSEL_STIFFNESS, 0.001f)
        assertEquals(carousel["damping"]!!.jsonPrimitive.float, PrismMotion.SpringParams.CAROUSEL_DAMPING, 0.001f)

        val expand = spring["expand"]!!.jsonObject
        assertEquals(expand["stiffness"]!!.jsonPrimitive.float, PrismMotion.SpringParams.EXPAND_STIFFNESS, 0.001f)
        assertEquals(expand["damping"]!!.jsonPrimitive.float, PrismMotion.SpringParams.EXPAND_DAMPING, 0.001f)

        val blur = root["blur"]!!.jsonObject
        assertEquals(blur["enter"]!!.jsonPrimitive.float, PrismMotion.Blur.ENTER, 0.001f)
        assertEquals(blur["exit"]!!.jsonPrimitive.float, PrismMotion.Blur.EXIT, 0.001f)

        val scale = root["scale"]!!.jsonObject
        assertEquals(scale["enter"]!!.jsonPrimitive.float, PrismMotion.Scale.ENTER, 0.001f)
        assertEquals(scale["carouselSide"]!!.jsonPrimitive.float, PrismMotion.Scale.CAROUSEL_SIDE, 0.001f)

        val gesture = root["gesture"]!!.jsonObject
        assertEquals(gesture["commitDistance"]!!.jsonPrimitive.float, PrismMotion.Gesture.COMMIT_DISTANCE, 0.001f)
        assertEquals(gesture["commitVelocity"]!!.jsonPrimitive.float, PrismMotion.Gesture.COMMIT_VELOCITY, 0.001f)
        assertEquals(gesture["rubberBand"]!!.jsonPrimitive.float, PrismMotion.Gesture.RUBBER_BAND, 0.001f)
    }

    @Test
    fun rubberBandIsMonotonicAndAsymptoticallyBounded() {
        val maxExtent = 1000f
        val limit = maxExtent * PrismMotion.Gesture.RUBBER_BAND
        val small = PrismMotion.rubberBand(50f, maxExtent)
        val large = PrismMotion.rubberBand(10_000f, maxExtent)
        assertTrue(small > 0f && small < 50f)
        assertTrue(large > small && large < limit)
        assertEquals(-small, PrismMotion.rubberBand(-50f, maxExtent), 0.001f)
    }
}
