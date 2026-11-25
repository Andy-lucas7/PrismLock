package com.example.prismlock

import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.LayerType
import com.example.prismlock.model.Project
import com.example.prismlock.model.ensureSingleRootDepth
import com.example.prismlock.model.isAboveSubject
import com.example.prismlock.model.migrated
import com.example.prismlock.model.setAboveSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthLayerOrderTest {
    @Test
    fun setAboveSubjectMovesRootLayerAndStackChildrenAcrossDepth() {
        val date = LayerNode(id = "date", name = "Data")
        val clock = LayerNode(id = "clock", name = "Hora")
        val depth = LayerNode(id = "depth", name = "Sujeito 3D", type = LayerType.DEPTH)
        val child = LayerNode(id = "temp", name = "Temp")
        val stack = LayerNode(id = "stack", name = "Widgets", type = LayerType.STACK, children = listOf(child))
        val layers = listOf(date, clock, depth, stack)

        assertFalse(layers.isAboveSubject("date"))
        assertFalse(layers.isAboveSubject("clock"))
        assertTrue(layers.isAboveSubject("stack"))
        assertTrue(layers.isAboveSubject("temp"))

        val dateInFront = layers.setAboveSubject("date", above = true)
        assertTrue(dateInFront.isAboveSubject("date"))
        assertEquals(listOf("clock", "depth", "date", "stack"), dateInFront.map { it.id })

        val stackBehind = dateInFront.setAboveSubject("temp", above = false)
        assertFalse(stackBehind.isAboveSubject("stack"))
        assertFalse(stackBehind.isAboveSubject("temp"))
        assertEquals(listOf("clock", "stack", "depth", "date"), stackBehind.map { it.id })
    }

    @Test
    fun ensureSingleRootDepthRemovesDuplicatesAndNestedDepth() {
        val d1 = LayerNode(id = "d1", name = "Sujeito 3D", type = LayerType.DEPTH)
        val d2 = LayerNode(id = "d2", name = "Sujeito 3D 2", type = LayerType.DEPTH)
        val nestedDepth = LayerNode(id = "d3", name = "Nested", type = LayerType.DEPTH)
        val stack = LayerNode(id = "s", name = "Stack", type = LayerType.STACK, children = listOf(LayerNode(id = "t"), nestedDepth))
        val project = Project(layers = listOf(d1, stack, d2)).migrated()

        assertEquals(listOf("d1", "s"), project.layers.map { it.id })
        assertEquals(listOf("t"), project.layers[1].children.map { it.id })
        assertEquals(project.layers, project.layers.ensureSingleRootDepth())
    }
}
