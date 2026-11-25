package com.example.prismlock

import com.example.prismlock.data.Env
import com.example.prismlock.engine.KustomFormulaEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class FxParityCasesTest {
    @Test
    fun sharedFxParityCasesMatchKustomFormulaEngine() {
        val file = listOf(File("docs/fx_parity_cases.json"), File("../docs/fx_parity_cases.json"))
            .firstOrNull { it.isFile }
            ?: error("docs/fx_parity_cases.json not found")
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val now = LocalDateTime.parse(root["timestamp"]!!.jsonPrimitive.content)
        val envObj = root["env"]!!.jsonObject
        val env = Env(
            battery = envObj["battery"]!!.jsonPrimitive.int,
            charging = envObj["charging"]!!.jsonPrimitive.boolean,
            temp = envObj["temp"]!!.jsonPrimitive.int,
            isDay = envObj["isDay"]!!.jsonPrimitive.boolean,
            city = envObj["city"]!!.jsonPrimitive.content
        )
        val globals = root["globals"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }

        for (caseEl in root["cases"]!!.jsonArray) {
            val obj = caseEl.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val expr = obj["expr"]!!.jsonPrimitive.content
            val expected = obj["expected"]!!.jsonPrimitive.content
            val actual = KustomFormulaEngine.evalDollarBlocks(expr, env, now, globals)
            assertEquals("Parity mismatch for case '$name' ($expr)", expected, actual)
        }
    }
}
