package com.example.prismlock

import com.example.prismlock.data.Env
import com.example.prismlock.engine.KustomFormulaEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class PrismStage7Test {
    @Test
    fun evalGvEvaluatesNestedFormulasAndStopsCyclesSafely() {
        val now = LocalDateTime.of(2026, 11, 25, 18, 45, 0)
        val env = Env(battery = 82)
        val globals = mapOf(
            "bat_label" to "\$bi(level)\$%",
            "nested" to "Bateria: \$gv(bat_label)\$",
            "loop_a" to "\$gv(loop_b)\$",
            "loop_b" to "\$gv(loop_a)\$"
        )

        assertEquals("82%", KustomFormulaEngine.evalDollarBlocks("\$gv(bat_label)\$", env, now, globals))
        assertEquals("Bateria: 82%", KustomFormulaEngine.evalDollarBlocks("\$gv(nested)\$", env, now, globals))
        // Must terminate safely without StackOverflowError
        val cyclic = KustomFormulaEngine.evalDollarBlocks("\$gv(loop_a)\$", env, now, globals)
        assertEquals("\$gv(loop_b)\$", cyclic)
    }
}
