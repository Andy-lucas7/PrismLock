package com.example.prismlock.engine

import com.example.prismlock.data.Env
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object Dynamic {
    private val fmtCache = HashMap<String, DateTimeFormatter?>()
    private val textCache = HashMap<String, String>()
    private var cacheStamp = ""

    val tags = listOf("{HH}", "{mm}", "{ss}", "{H}", "{hh}", "{a}", "{EEEE}", "{EEEE_UPPER}", "{EEE}", "{d}", "{dd}", "{MMMM}", "{MMM}", "{MM}",
        "{yyyy/MM/dd}", "{yyyy. MM. dd.}", "{bat}", "{charging}", "{temp}", "{cond}", "{city}", "{alarm}", "{steps}", "{dayprog}", "{=bat*2}")

    fun weatherText(code: Int): String = when (code) {
        0 -> "Céu limpo"; 1, 2 -> "Parcialmente nublado"; 3 -> "Nublado"; 45, 48 -> "Neblina"
        in 51..57 -> "Garoa"; in 61..67, in 80..82 -> "Chuva"; in 71..77, 85, 86 -> "Neve"; in 95..99 -> "Tempestade"; else -> "—"
    }

    fun vars(env: Env, now: LocalDateTime): Map<String, Double> {
        val secs = now.toLocalTime().toSecondOfDay()
        return mapOf(
            "bat" to env.battery.toDouble(), "charging" to if (env.charging) 1.0 else 0.0, "temp" to (env.temp ?: 0).toDouble(),
            "steps" to env.steps.toDouble(), "wcode" to env.weatherCode.toDouble(), "isday" to if (env.isDay) 1.0 else 0.0,
            "hour" to now.hour.toDouble(), "minute" to now.minute.toDouble(), "second" to now.second.toDouble(),
            "day" to now.dayOfMonth.toDouble(), "month" to now.monthValue.toDouble(), "dow" to now.dayOfWeek.value.toDouble(),
            "dayprog" to secs / 864.0, "hourprog" to (now.minute * 60 + now.second) / 36.0, "locked" to if (env.locked) 1.0 else 0.0,
            "pi" to Math.PI
        )
    }

    fun eval(text: String, env: Env, now: LocalDateTime): String {
        if ('{' !in text) return text
        val stamp = "${now.withSecond(0).withNano(0)}|$env"
        if (stamp != cacheStamp) { textCache.clear(); cacheStamp = stamp }
        textCache[text]?.let { return it }
        val v by lazy { vars(env, now) }
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '{') {
                val j = text.indexOf('}', i + 1)
                if (j < 0) { sb.append(text, i, text.length); break }
                sb.append(tag(text.substring(i + 1, j), env, now) { v })
                i = j + 1
            } else { sb.append(c); i++ }
        }
        return sb.toString().also { textCache[text] = it }
    }

    private fun tag(raw: String, env: Env, now: LocalDateTime, v: () -> Map<String, Double>): String {
        var t = raw; var case = 0
        if (t.endsWith("_UPPER")) { t = t.removeSuffix("_UPPER"); case = 1 } else if (t.endsWith("_LOWER")) { t = t.removeSuffix("_LOWER"); case = 2 }
        val out = when {
            t.startsWith("=") -> fmtNum(runCatching { Expr(t.substring(1), v()).parse() }.getOrDefault(Double.NaN))
            t == "bat" -> env.battery.toString()
            t == "charging" -> if (env.charging) "⚡" else ""
            t == "temp" -> env.temp?.toString() ?: "--"
            t == "city" -> env.city
            t == "cond" -> weatherText(env.weatherCode)
            t == "alarm" -> env.alarm.ifEmpty { "--:--" }
            t == "steps" -> env.steps.toString()
            t == "dayprog" -> (v()["dayprog"] ?: 0.0).toInt().toString()
            else -> {
                val f = fmtCache.getOrPut(t) { runCatching { DateTimeFormatter.ofPattern(t, Locale.getDefault()) }.getOrNull() }
                f?.let { runCatching { it.format(now) }.getOrNull() } ?: "{$raw}"
            }
        }
        return when (case) { 1 -> out.uppercase(); 2 -> out.lowercase(); else -> out }
    }

    private fun fmtNum(d: Double): String = when {
        d.isNaN() -> "?"
        d == floor(d) && abs(d) < 1e12 -> d.roundToLong().toString()
        else -> "%.1f".format(d)
    }

    fun number(expr: String, env: Env, now: LocalDateTime): Double {
        val e = expr.trim().removePrefix("{").removeSuffix("}").removePrefix("=")
        return runCatching { Expr(e, vars(env, now)).parse() }.getOrDefault(0.0)
    }
}

/**
 * Recursive-descent parser. Grammar (lowest → highest precedence):
 *   ternary := or ('?' ternary ':' ternary)?
 *   or      := and ('||' and)*
 *   and     := cmp ('&&' cmp)*
 *   cmp     := add (('<'|'>'|'<='|'>='|'=='|'!=') add)?
 *   add     := mul (('+'|'-') mul)*
 *   mul     := pow (('*'|'/'|'%') pow)*
 *   pow     := unary ('^' pow)?            right-associative
 *   unary   := ('-'|'!') unary | atom
 *   atom    := number | ident | ident '(' args ')' | '(' ternary ')'
 * Booleans are 1.0 / 0.0.
 */
class Expr(private val s: String, private val vars: Map<String, Double>) {
    private var p = 0

    fun parse(): Double { val r = ternary(); ws(); require(p >= s.length) { "trailing" }; return r }

    private fun ws() { while (p < s.length && s[p].isWhitespace()) p++ }
    private fun eat(tok: String): Boolean { ws(); if (s.startsWith(tok, p)) { p += tok.length; return true }; return false }
    private fun peekOp(vararg ops: String): String? { ws(); return ops.firstOrNull { s.startsWith(it, p) } }

    private fun ternary(): Double {
        val c = or()
        if (eat("?")) { val a = ternary(); require(eat(":")); val b = ternary(); return if (c != 0.0) a else b }
        return c
    }
    private fun or(): Double { var l = and(); while (eat("||")) { val r = and(); l = if (l != 0.0 || r != 0.0) 1.0 else 0.0 }; return l }
    private fun and(): Double { var l = cmp(); while (eat("&&")) { val r = cmp(); l = if (l != 0.0 && r != 0.0) 1.0 else 0.0 }; return l }
    private fun cmp(): Double {
        val l = add()
        val op = peekOp("<=", ">=", "==", "!=", "<", ">") ?: return l
        p += op.length
        val r = add()
        val b = when (op) { "<=" -> l <= r; ">=" -> l >= r; "==" -> l == r; "!=" -> l != r; "<" -> l < r; else -> l > r }
        return if (b) 1.0 else 0.0
    }
    private fun add(): Double {
        var l = mul()
        while (true) { l = when { eat("+") -> l + mul(); peekOp("-") != null -> { p++; l - mul() }; else -> return l } }
    }
    private fun mul(): Double {
        var l = pow()
        while (true) { l = when { eat("*") -> l * pow(); eat("/") -> l / pow(); eat("%") -> l % pow(); else -> return l } }
    }
    private fun pow(): Double { val b = unary(); return if (eat("^")) b.pow(pow()) else b }
    private fun unary(): Double = when {
        eat("-") -> -unary()
        peekOp("!") != null && !s.startsWith("!=", p) -> { p++; if (unary() == 0.0) 1.0 else 0.0 }
        else -> atom()
    }
    private fun atom(): Double {
        ws()
        if (eat("(")) { val v = ternary(); require(eat(")")); return v }
        val st = p
        if (p < s.length && (s[p].isDigit() || s[p] == '.')) {
            while (p < s.length && (s[p].isDigit() || s[p] == '.')) p++
            return s.substring(st, p).toDouble()
        }
        while (p < s.length && (s[p].isLetterOrDigit() || s[p] == '_')) p++
        val id = s.substring(st, p).lowercase()
        require(id.isNotEmpty()) { "unexpected" }
        if (eat("(")) {
            val args = mutableListOf<Double>()
            if (!eat(")")) { do { args += ternary() } while (eat(",")); require(eat(")")) }
            return fn(id, args)
        }
        return vars[id] ?: error("var $id")
    }
    private fun fn(id: String, a: List<Double>): Double = when (id) {
        "min" -> a.min(); "max" -> a.max(); "abs" -> abs(a[0]); "round" -> Math.round(a[0]).toDouble()
        "floor" -> floor(a[0]); "ceil" -> ceil(a[0]); "sqrt" -> sqrt(a[0]); "sin" -> sin(a[0]); "cos" -> cos(a[0])
        "clamp" -> min(max(a[0], a[1]), a[2]); "if" -> if (a[0] != 0.0) a[1] else a[2]
        else -> error("fn $id")
    }
}
