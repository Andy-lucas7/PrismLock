package com.example.prismlock

import com.example.prismlock.data.Env
import com.example.prismlock.data.ProjectStore
import com.example.prismlock.engine.Dynamic
import com.example.prismlock.engine.KustomFormulaEngine
import com.example.prismlock.model.GlobalType
import com.example.prismlock.model.GlobalVar
import com.example.prismlock.model.LayerNode
import com.example.prismlock.model.Project
import com.example.prismlock.model.TouchAction
import com.example.prismlock.model.TouchActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class KustomFormulaTest {
    private val fixedNow = LocalDateTime.of(2026, 11, 17, 18, 45, 30)
    private val sampleEnv = Env(
        battery = 60,
        charging = true,
        temp = 24,
        city = "São Paulo",
        alarm = "20:00",
        locked = false,
        mediaVolume = 75,
        mediaPlaying = true
    )

    @Test
    fun globalsAndTruthyAndColorEvalWork() {
        val globals = mapOf("painel" to "1", "modo" to "Escuro", "cor_principal" to "#FF7AA7FF", "titulo" to "PrismLock")
        assertEquals("1", Dynamic.eval("\$gv(painel, \"0\")\$", sampleEnv, fixedNow, globals))
        assertEquals("padrão", Dynamic.eval("\$gv(inexistente, \"padrão\")\$", sampleEnv, fixedNow, globals))
        assertEquals("PrismLock", Dynamic.eval("{titulo}", sampleEnv, fixedNow, globals))
        assertEquals("#FF7AA7FF", Dynamic.eval("{cor_principal}", sampleEnv, fixedNow, globals))
        assertTrue(KustomFormulaEngine.evalTruthy("\$gv(painel) = 1\$", sampleEnv, fixedNow, globals))
        assertFalse(KustomFormulaEngine.evalTruthy("\$gv(painel) = 0\$", sampleEnv, fixedNow, globals))

        val col = KustomFormulaEngine.evalColor("\$if(bi(level) > 50, \"#FF00FF88\", \"#FFFF0000\")\$", sampleEnv, fixedNow, globals)
        assertEquals(0xFF00FF88.toInt(), col)

        val expectedPrincipal = 0xFF7AA7FF.toInt()
        assertEquals(expectedPrincipal, KustomFormulaEngine.evalColor("\$gv(cor_principal)\$", sampleEnv, fixedNow, globals))
        assertEquals(expectedPrincipal, KustomFormulaEngine.evalColor("gv(cor_principal)", sampleEnv, fixedNow, globals))
        assertEquals(expectedPrincipal, KustomFormulaEngine.evalColor("{cor_principal}", sampleEnv, fixedNow, globals))
        assertEquals(expectedPrincipal, KustomFormulaEngine.evalColor("cor_principal", sampleEnv, fixedNow, globals))
    }

    @Test
    fun expandedKustomFunctionsComputeCorrectly() {
        assertEquals("1h 14m", KustomFormulaEngine.evalDollarBlocks("\$tf(\"20:00\")\$", sampleEnv, fixedNow))
        assertEquals("#FF000000", KustomFormulaEngine.evalDollarBlocks("\$ce(\"#FFFFFFFF\", \"contrast\")\$", sampleEnv, fixedNow))
        assertEquals("#FFFFFFFF", KustomFormulaEngine.evalDollarBlocks("\$ce(\"#FF101010\", \"contrast\")\$", sampleEnv, fixedNow))
        assertEquals("1-2-3", KustomFormulaEngine.evalDollarBlocks("\$fl(1, 3, 1, \"i\", \"-\")\$", sampleEnv, fixedNow))
        assertEquals("007", KustomFormulaEngine.evalDollarBlocks("\$tc(lpad, \"7\", 3, \"0\")\$", sampleEnv, fixedNow))
        assertEquals("Paulo", KustomFormulaEngine.evalDollarBlocks("\$tc(split, \"São Paulo\", \" \", 1)\$", sampleEnv, fixedNow))
        assertEquals("9", KustomFormulaEngine.evalDollarBlocks("\$mu(pow, 3, 2)\$", sampleEnv, fixedNow))
        assertEquals("180", KustomFormulaEngine.evalDollarBlocks("\$df(ss) * 6\$", sampleEnv, fixedNow))
    }

    @Test
    fun projectGlobalsMutationsAndJsonRoundTripAreSafe() {
        val p = Project(
            id = "test_proj",
            globals = listOf(
                GlobalVar("sw", GlobalType.SWITCH, "0"),
                GlobalVar("aba", GlobalType.LIST, "A", listOf("A", "B", "C")),
                GlobalVar("cor", GlobalType.COLOR, "#FF7AA7FF")
            ),
            layers = listOf(
                LayerNode(
                    id = "n1",
                    text = "\$gv(aba)\$",
                    touch = TouchAction(TouchActionType.NEXT_GLOBAL_LIST, globalName = "aba"),
                    visibilityExpr = "\$gv(sw) = 1\$",
                    fxColor = "\$gv(cor)\$",
                    fxRotation = "\$df(ss) * 6\$"
                )
            )
        )
        assertEquals(0xFF7AA7FF.toInt(), KustomFormulaEngine.evalColor("\$gv(cor)\$", sampleEnv, fixedNow, p.globalsMap()))
        val toggled = p.toggleGlobal("sw")
        assertEquals("1", toggled.globalsMap()["sw"])
        val cycled = toggled.cycleGlobal("aba")
        assertEquals("B", cycled.globalsMap()["aba"])

        val encoded = ProjectStore.json.encodeToString(Project.serializer(), cycled)
        val decoded = ProjectStore.json.decodeFromString(Project.serializer(), encoded)
        assertNotNull(decoded)
        assertEquals("B", decoded.globalsMap()["aba"])
        assertEquals(TouchActionType.NEXT_GLOBAL_LIST, decoded.layers.first().touch.type)
        assertEquals("\$gv(sw) = 1\$", decoded.layers.first().visibilityExpr)

        // Legacy minimal JSON still decodes cleanly with defaults
        val legacyJson = """{"id":"legacy","title":"Old"}"""
        val legacy = ProjectStore.json.decodeFromString(Project.serializer(), legacyJson)
        assertTrue(legacy.globals.isEmpty())
    }
}
