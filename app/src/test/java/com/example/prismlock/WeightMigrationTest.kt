package com.example.prismlock

import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.migrated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class WeightMigrationTest {
    @Test
    fun migratesLegacyWghtAxisIntoWeightAndStripsDuplicateKey() {
        val legacyChild = LayerNode(
            name = "Hora",
            weight = 400,
            axes = mapOf("wght" to 725.6f, "wdth" to 110f)
        )
        val stack = LayerNode(
            name = "Stack",
            type = LayerType.STACK,
            children = listOf(legacyChild)
        )
        val project = Project(layers = listOf(stack))

        val migrated = project.migrated()
        val child = migrated.layers.first().children.first()

        assertEquals(726, child.weight)
        assertFalse("wght" in child.axes)
        assertEquals(110f, child.axes["wdth"])
    }

    @Test
    fun clampsOutOfRangeAndIgnoresNonFiniteLegacyWght() {
        val over = LayerNode(weight = 400, axes = mapOf("wght" to 1500f)).migrated()
        val under = LayerNode(weight = 400, axes = mapOf("wght" to -20f)).migrated()
        val nan = LayerNode(weight = 550, axes = mapOf("wght" to Float.NaN)).migrated()

        assertEquals(1000, over.weight)
        assertFalse("wght" in over.axes)
        assertEquals(1, under.weight)
        assertFalse("wght" in under.axes)
        assertEquals(550, nan.weight)
        assertFalse("wght" in nan.axes)
    }

    @Test
    fun cleanProjectReturnsSameInstanceWithoutAllocating() {
        val clean = Project(
            layers = listOf(
                LayerNode(name = "A", weight = 600, axes = mapOf("wdth" to 100f))
            )
        )
        assertSame(clean, clean.migrated())
    }
}
