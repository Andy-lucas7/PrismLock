package com.example.prismlock.engine

import android.media.AudioManager
import android.os.Build
import com.example.prismlock.data.Env
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class FormulaCategory(
    val id: String,
    val label: String,
    val items: List<FormulaSnippet>
)

data class FormulaSnippet(
    val title: String,
    val code: String
)

/**
 * Motor completo de fórmulas estilo KWGT / KLWP ($...$):
 * Suporta df(), bi(), si(), wi(), if(), tc(), operadores (=, !=, <, >, <=, >=, &, |, +, -, *, /, %)
 * e retrocompatibilidade total com Dynamic ({HH}, {mm}, {=expr}).
 */
object KustomFormulaEngine {
    private val fmtCache = HashMap<String, DateTimeFormatter?>()

    val categories: List<FormulaCategory> = listOf(
        FormulaCategory(
            id = "datetime",
            label = "Data/Hora",
            items = listOf(
                FormulaSnippet("Hora 24h", "\$df(HH)\$"),
                FormulaSnippet("Minuto", "\$df(mm)\$"),
                FormulaSnippet("Segundo", "\$df(ss)\$"),
                FormulaSnippet("Relógio", "\$df(HH)\$:\$df(mm)\$"),
                FormulaSnippet("Dia Semana", "\$df(EEEE)\$"),
                FormulaSnippet("Dia Abrev.", "\$df(EEE)\$"),
                FormulaSnippet("Data Br", "\$df(dd/MM/yyyy)\$"),
                FormulaSnippet("Mês", "\$df(MMMM)\$")
            )
        ),
        FormulaCategory(
            id = "battery",
            label = "Bateria/Sistema",
            items = listOf(
                FormulaSnippet("Bateria %", "\$bi(level)\$%"),
                FormulaSnippet("Status Carga", "\$bi(status)\$"),
                FormulaSnippet("Temp. Bat.", "\$bi(temp)\$°C"),
                FormulaSnippet("Próx. Alarme", "\$si(alarm)\$"),
                FormulaSnippet("Volume Mídia", "\$si(volm)\$%"),
                FormulaSnippet("Tocando Mídia", "\$if(si(mplaying), \"▶ Tocando\", \"⏸ Pausado\")\$"),
                FormulaSnippet("Modo Som", "\$si(ringer)\$"),
                FormulaSnippet("Aparelho", "\$si(model)\$")
            )
        ),
        FormulaCategory(
            id = "weather",
            label = "Clima",
            items = listOf(
                FormulaSnippet("Temperatura", "\$wi(temp)\$°"),
                FormulaSnippet("Condição", "\$wi(cond)\$"),
                FormulaSnippet("Cidade", "\$wi(city)\$"),
                FormulaSnippet("Resumo", "\$wi(temp)\$° · \$wi(cond)\$")
            )
        ),
        FormulaCategory(
            id = "logic",
            label = "Lógica / KLWP",
            items = listOf(
                FormulaSnippet("Global gv()", "\$gv(painel, \"0\")\$"),
                FormulaSnippet("Alerta Bateria", "\$if(bi(level) < 20, \"Bateria Baixa\", \"Normal\")\$"),
                FormulaSnippet("Se Carregando", "\$if(bi(status) = \"Carregando\", \"⚡ Carregando\", bi(level) + \"%\")\$"),
                FormulaSnippet("Contagem Alarme", "\$tf(si(alarm))\$"),
                FormulaSnippet("Barra Pontos", "\$fl(1, 5, 1, \"if(i <= bi(level)/20, \\\"●\\\", \\\"○\\\")\")\$"),
                FormulaSnippet("Dia MAIÚSCULO", "\$tc(up, df(EEEE))\$"),
                FormulaSnippet("Capitalizar", "\$tc(cap, df(EEEE))\$")
            )
        ),
        FormulaCategory(
            id = "fx",
            label = "FX / Cores",
            items = listOf(
                FormulaSnippet("Cor Bateria", "\$if(bi(level) <= 20, \"#FFFF5252\", \"#FFFFFFFF\")\$"),
                FormulaSnippet("Cor Contraste", "\$ce(\"#FF111318\", \"contrast\")\$"),
                FormulaSnippet("Misturar Cores", "\$ce(\"#FFFFFFFF\", \"mix\", \"#FF7AA7FF\", 50)\$"),
                FormulaSnippet("Ponteiros Seg.", "\$df(ss) * 6\$"),
                FormulaSnippet("Visível se Global", "\$gv(painel) = 1\$")
            )
        ),
        FormulaCategory(
            id = "legacy",
            label = "Tags Legadas",
            items = listOf(
                FormulaSnippet("{HH}:{mm}", "{HH}:{mm}"),
                FormulaSnippet("{EEEE}", "{EEEE}"),
                FormulaSnippet("{bat}%", "{bat}%"),
                FormulaSnippet("{temp}°", "{temp}°"),
                FormulaSnippet("{cond}", "{cond}"),
                FormulaSnippet("{alarm}", "{alarm}"),
                FormulaSnippet("{=bat*2}", "{=bat*2}")
            )
        )
    )

    fun hasSecondsToken(text: String): Boolean =
        "{ss}" in text || ("\$df(" in text && "ss" in text) || ("\$tf(" in text && "\"S\"" in text)

    fun evalDollarBlocks(
        text: String,
        env: Env,
        now: LocalDateTime,
        globals: Map<String, String> = emptyMap()
    ): String {
        if ('$' !in text) return text
        val sb = StringBuilder(text.length + 16)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '$') {
                val close = findClosingDollar(text, i + 1)
                if (close < 0) {
                    sb.append(text, i, text.length)
                    break
                }
                val expr = text.substring(i + 1, close)
                val evaluated = runCatching { Parser(expr, env, now, globals).parseExpr().text }
                    .getOrElse { "" }
                sb.append(evaluated)
                i = close + 1
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    fun resolveValue(
        expr: String,
        env: Env,
        now: LocalDateTime,
        globals: Map<String, String> = emptyMap()
    ): String {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return ""
        val firstPass = when {
            '$' in trimmed || '{' in trimmed -> Dynamic.eval(trimmed, env, now, globals).trim()
            else -> {
                val directGlobal = globals[trimmed.lowercase()]
                if (directGlobal != null) {
                    directGlobal.trim()
                } else if ('(' in trimmed) {
                    runCatching { Parser(trimmed, env, now, globals).parseExpr().text.trim() }.getOrDefault(trimmed)
                } else {
                    trimmed
                }
            }
        }
        val secondPass = globals[firstPass.lowercase()]?.trim() ?: firstPass
        return if ('$' in secondPass || '{' in secondPass) {
            Dynamic.eval(secondPass, env, now, globals).trim()
        } else {
            secondPass
        }
    }

    fun evalNumeric(
        expr: String,
        env: Env,
        now: LocalDateTime,
        globals: Map<String, String> = emptyMap()
    ): Double? {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return null
        globals[trimmed.lowercase()]?.let { gVal ->
            gVal.trim().toDoubleOrNull()?.let { return it }
            if ('$' in gVal || '(' in gVal) {
                evalNumeric(gVal, env, now, globals)?.let { return it }
            }
        }
        if ('$' in trimmed || '{' in trimmed) {
            val evaluated = Dynamic.eval(trimmed, env, now, globals).trim().removeSuffix("%").trim()
            evaluated.toDoubleOrNull()?.let { return it }
            runCatching { Expr(evaluated, Dynamic.vars(env, now, globals)).parse() }.getOrNull()?.let { return it }
        }
        val inner = if (trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
        return runCatching {
            val v = Parser(inner, env, now, globals).parseExpr()
            v.num ?: v.text.toDoubleOrNull()
        }.getOrNull()
    }

    fun evalTruthy(
        expr: String,
        env: Env,
        now: LocalDateTime,
        globals: Map<String, String> = emptyMap()
    ): Boolean {
        val trimmed = expr.trim()
        if (trimmed.isEmpty()) return true
        globals[trimmed.lowercase()]?.let { gVal ->
            val gt = gVal.trim()
            gt.toDoubleOrNull()?.let { return it != 0.0 }
            if (gt.equals("false", ignoreCase = true)) return false
            if (gt.equals("true", ignoreCase = true)) return true
            return gt.isNotEmpty()
        }
        val inner = if (trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            Dynamic.eval(trimmed, env, now, globals).trim()
        }
        if (trimmed.startsWith("$") && trimmed.endsWith("$") && inner.count { it == '$' } == 0) {
            return runCatching { Parser(inner, env, now, globals).parseExpr().isTruthy }.getOrDefault(false)
        }
        val evaluated = Dynamic.eval(trimmed, env, now, globals).trim()
        if (evaluated.isEmpty()) return false
        evaluated.toDoubleOrNull()?.let { return it != 0.0 }
        if (evaluated.equals("false", ignoreCase = true)) return false
        if (evaluated.equals("true", ignoreCase = true)) return true
        return runCatching { Parser(evaluated, env, now, globals).parseExpr().isTruthy }.getOrDefault(evaluated.isNotEmpty())
    }

    fun evalColor(
        expr: String,
        env: Env,
        now: LocalDateTime,
        globals: Map<String, String> = emptyMap()
    ): Int? {
        val evaluated = resolveValue(expr, env, now, globals)
        if (evaluated.isEmpty()) return null
        return parseHexOrIntColor(evaluated)
    }

    fun parseHexOrIntColor(raw: String): Int? = parseColorSafe(raw)

    private fun parseColorSafe(raw: String): Int? {
        val s = raw.trim().removePrefix("#")
        return when (s.length) {
            6 -> s.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
            8 -> s.toLongOrNull(16)?.toInt()
            else -> raw.trim().toIntOrNull()
        }
    }

    private fun findClosingDollar(s: String, start: Int): Int {
        var i = start
        var inQuote: Char? = null
        while (i < s.length) {
            val c = s[i]
            if (inQuote != null) {
                if (c == '\\' && i + 1 < s.length) {
                    i += 2
                    continue
                }
                if (c == inQuote) inQuote = null
            } else {
                when (c) {
                    '"', '\'' -> inQuote = c
                    '$' -> return i
                }
            }
            i++
        }
        return -1
    }

    private data class KVal(val text: String, val num: Double?) {
        val isTruthy: Boolean
            get() = when {
                num != null -> !num.isNaN() && num != 0.0
                else -> text.isNotEmpty() && !text.equals("false", ignoreCase = true) && text != "0"
            }

        companion object {
            fun ofText(s: String): KVal = KVal(s, s.toDoubleOrNull())
            fun ofNum(d: Double): KVal {
                val fmt = when {
                    d.isNaN() -> "0"
                    d == floor(d) && abs(d) < 1e12 -> d.roundToLong().toString()
                    else -> "%.1f".format(Locale.US, d)
                }
                return KVal(fmt, d)
            }
            fun ofBool(b: Boolean): KVal = if (b) KVal("1", 1.0) else KVal("0", 0.0)
        }
    }

    private class Parser(
        private val src: String,
        private val env: Env,
        private val now: LocalDateTime,
        private val globals: Map<String, String> = emptyMap(),
        private val locals: Map<String, KVal> = emptyMap()
    ) {
        private var p = 0

        fun parseExpr(): KVal = parseOr()

        private fun ws() {
            while (p < src.length && src[p].isWhitespace()) p++
        }

        private fun eat(tok: String): Boolean {
            ws()
            if (src.startsWith(tok, p)) {
                p += tok.length
                return true
            }
            return false
        }

        private fun peekOp(vararg ops: String): String? {
            ws()
            return ops.firstOrNull { src.startsWith(it, p) }
        }

        private fun parseOr(): KVal {
            var left = parseAnd()
            while (true) {
                if (eat("||") || eat("|")) {
                    val right = parseAnd()
                    left = KVal.ofBool(left.isTruthy || right.isTruthy)
                } else break
            }
            return left
        }

        private fun parseAnd(): KVal {
            var left = parseCmp()
            while (true) {
                if (eat("&&") || eat("&")) {
                    val right = parseCmp()
                    left = KVal.ofBool(left.isTruthy && right.isTruthy)
                } else break
            }
            return left
        }

        private fun parseCmp(): KVal {
            val left = parseAdd()
            val op = peekOp("<=", ">=", "==", "!=", "=", "<", ">") ?: return left
            p += op.length
            val right = parseAdd()
            val res = if (left.num != null && right.num != null) {
                when (op) {
                    "<=" -> left.num <= right.num
                    ">=" -> left.num >= right.num
                    "==", "=" -> left.num == right.num
                    "!=" -> left.num != right.num
                    "<" -> left.num < right.num
                    else -> left.num > right.num
                }
            } else {
                when (op) {
                    "<=" -> left.text <= right.text
                    ">=" -> left.text >= right.text
                    "==", "=" -> left.text == right.text
                    "!=" -> left.text != right.text
                    "<" -> left.text < right.text
                    else -> left.text > right.text
                }
            }
            return KVal.ofBool(res)
        }

        private fun parseAdd(): KVal {
            var left = parseMul()
            while (true) {
                when {
                    eat("+") -> {
                        val right = parseMul()
                        left = if (left.num != null && right.num != null) {
                            KVal.ofNum(left.num + right.num)
                        } else {
                            KVal.ofText(left.text + right.text)
                        }
                    }
                    peekOp("-") != null -> {
                        p++
                        val right = parseMul()
                        val l = left.num ?: 0.0
                        val r = right.num ?: 0.0
                        left = KVal.ofNum(l - r)
                    }
                    else -> return left
                }
            }
        }

        private fun parseMul(): KVal {
            var left = parseUnary()
            while (true) {
                when {
                    eat("*") -> {
                        val right = parseUnary()
                        left = KVal.ofNum((left.num ?: 0.0) * (right.num ?: 0.0))
                    }
                    eat("/") -> {
                        val right = parseUnary()
                        val r = right.num ?: 1.0
                        left = KVal.ofNum(if (abs(r) < 1e-9) 0.0 else (left.num ?: 0.0) / r)
                    }
                    eat("%") -> {
                        val right = parseUnary()
                        val r = right.num ?: 1.0
                        left = KVal.ofNum(if (abs(r) < 1e-9) 0.0 else (left.num ?: 0.0) % r)
                    }
                    else -> return left
                }
            }
        }

        private fun parseUnary(): KVal = when {
            eat("-") -> {
                val v = parseUnary()
                KVal.ofNum(-(v.num ?: 0.0))
            }
            peekOp("!") != null && !src.startsWith("!=", p) -> {
                p++
                KVal.ofBool(!parseUnary().isTruthy)
            }
            else -> parsePrimary()
        }

        private fun parsePrimary(): KVal {
            ws()
            if (p >= src.length) return KVal.ofText("")
            val ch = src[p]
            if (ch == '(') {
                p++
                val v = parseExpr()
                eat(")")
                return v
            }
            if (ch == '"' || ch == '\'') {
                return KVal.ofText(readStringLiteral())
            }
            if (ch.isDigit() || (ch == '.' && p + 1 < src.length && src[p + 1].isDigit())) {
                val st = p
                while (p < src.length && (src[p].isDigit() || src[p] == '.')) p++
                val raw = src.substring(st, p)
                return KVal(raw, raw.toDoubleOrNull())
            }
            val st = p
            while (p < src.length && (src[p].isLetterOrDigit() || src[p] == '_')) p++
            val ident = src.substring(st, p)
            if (ident.isEmpty()) {
                p++
                return KVal.ofText(ch.toString())
            }
            ws()
            if (p < src.length && src[p] == '(') {
                return parseFunctionCall(ident.lowercase())
            }
            locals[ident.lowercase()]?.let { return it }
            return KVal.ofText(ident)
        }

        private fun readStringLiteral(): String {
            val quote = src[p++]
            val sb = StringBuilder()
            while (p < src.length) {
                val c = src[p++]
                if (c == '\\' && p < src.length) {
                    sb.append(src[p++])
                } else if (c == quote) {
                    break
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseFunctionCall(name: String): KVal {
            p++ // skip '('
            if (name == "df") {
                ws()
                val arg = if (p < src.length && src[p] != '"' && src[p] != '\'' && !hasTopLevelCallOrConcat()) {
                    readRawUntilCommaOrClose()
                } else {
                    parseExpr().text
                }
                eat(")")
                return evalDf(arg)
            }
            val args = ArrayList<KVal>(4)
            ws()
            if (p < src.length && src[p] != ')') {
                while (true) {
                    args += parseExpr()
                    ws()
                    if (eat(",")) continue
                    break
                }
            }
            eat(")")
            return evalFunction(name, args)
        }

        private fun hasTopLevelCallOrConcat(): Boolean {
            var idx = p
            var depth = 0
            while (idx < src.length) {
                val c = src[idx]
                if (c == '(' || c == '+') return true
                if (c == ')' && depth == 0) return false
                idx++
            }
            return false
        }

        private fun readRawUntilCommaOrClose(): String {
            val st = p
            while (p < src.length && src[p] != ')' && src[p] != ',') p++
            return src.substring(st, p).trim()
        }

        private fun evalFunction(name: String, args: List<KVal>): KVal = when (name) {
            "df" -> evalDf(args.getOrNull(0)?.text ?: "HH:mm")
            "tf" -> evalTf(args)
            "bi" -> evalBi(args.getOrNull(0)?.text ?: "level")
            "si" -> evalSi(args.getOrNull(0)?.text ?: "model")
            "wi" -> evalWi(args.getOrNull(0)?.text ?: "temp")
            "mi" -> evalMi(args.getOrNull(0)?.text ?: "state")
            "gv" -> evalGv(args)
            "ce" -> evalCe(args)
            "fl" -> evalFl(args)
            "if" -> evalIf(args)
            "tc" -> evalTc(args)
            "mu" -> evalMu(args)
            else -> KVal.ofText("")
        }

        private fun evalDf(pattern: String): KVal {
            if (pattern.isEmpty()) return KVal.ofText("")
            if (pattern == "D") return KVal.ofNum(now.dayOfYear.toDouble())
            if (pattern == "f" || pattern == "S") {
                return KVal.ofNum(now.atZone(java.time.ZoneId.systemDefault()).toEpochSecond().toDouble())
            }
            val fmt = fmtCache.getOrPut(pattern) {
                runCatching { DateTimeFormatter.ofPattern(pattern, Locale.getDefault()) }.getOrNull()
            }
            val formatted = fmt?.let { runCatching { it.format(now) }.getOrNull() } ?: pattern
            return KVal.ofText(formatted)
        }

        private fun evalTf(args: List<KVal>): KVal {
            val raw = args.getOrNull(0)?.text?.trim().orEmpty()
            if (raw.isEmpty() || raw == "--:--") return KVal.ofText("--")
            val targetDt: LocalDateTime = runCatching {
                when {
                    Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$").matches(raw) -> {
                        val parts = raw.split(":")
                        val t = LocalTime.of(
                            parts[0].toInt().coerceIn(0, 23),
                            parts[1].toInt().coerceIn(0, 59),
                            parts.getOrNull(2)?.toInt()?.coerceIn(0, 59) ?: 0
                        )
                        val today = now.toLocalDate().atTime(t)
                        if (today.isBefore(now)) today.plusDays(1) else today
                    }
                    Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(raw) -> {
                        LocalDate.parse(raw).atStartOfDay()
                    }
                    else -> LocalDateTime.parse(raw)
                }
            }.getOrNull() ?: return KVal.ofText("--")

            val secs = Duration.between(now, targetDt).seconds
            val mode = args.getOrNull(1)?.text?.trim()?.uppercase()
            return when (mode) {
                "D" -> KVal.ofNum((secs / 86400L).toDouble())
                "H" -> KVal.ofNum((secs / 3600L).toDouble())
                "M" -> KVal.ofNum((secs / 60L).toDouble())
                "S" -> KVal.ofNum(secs.toDouble())
                else -> {
                    val absS = abs(secs)
                    val d = absS / 86400L
                    val h = (absS % 86400L) / 3600L
                    val m = (absS % 3600L) / 60L
                    val prefix = if (secs < 0) "-" else ""
                    val out = when {
                        d > 0 -> "${prefix}${d}d ${h}h"
                        h > 0 -> "${prefix}${h}h ${m}m"
                        else -> "${prefix}${m}m"
                    }
                    KVal.ofText(out)
                }
            }
        }

        private fun evalBi(param: String): KVal = when (param.lowercase()) {
            "level" -> KVal.ofNum(env.battery.toDouble())
            "status" -> KVal.ofText(if (env.charging) "Carregando" else "Descarregando")
            "charging" -> KVal.ofBool(env.charging)
            "temp" -> KVal("%.1f".format(Locale.US, env.batteryTempC), env.batteryTempC.toDouble())
            else -> KVal.ofNum(env.battery.toDouble())
        }

        private fun evalSi(param: String): KVal = when (param.lowercase()) {
            "alarm" -> KVal.ofText(env.alarm.ifEmpty { "--:--" })
            "model" -> KVal.ofText(Build.MODEL ?: "Android")
            "man", "manufacturer" -> KVal.ofText(Build.MANUFACTURER ?: "Android")
            "brand" -> KVal.ofText(Build.BRAND ?: "Android")
            "sdk" -> KVal.ofNum(Build.VERSION.SDK_INT.toDouble())
            "locked" -> KVal.ofBool(env.locked)
            "steps" -> KVal.ofNum(env.steps.toDouble())
            "volm" -> KVal.ofNum(env.mediaVolume.toDouble())
            "mplaying" -> KVal.ofBool(env.mediaPlaying)
            "ringer" -> KVal.ofText(
                when (env.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> "Silencioso"
                    AudioManager.RINGER_MODE_VIBRATE -> "Vibrar"
                    else -> "Normal"
                }
            )
            else -> KVal.ofText("")
        }

        private fun evalMi(param: String): KVal = when (param.lowercase()) {
            "state" -> KVal.ofText(if (env.mediaPlaying) "PLAYING" else "PAUSED")
            "playing" -> KVal.ofBool(env.mediaPlaying)
            else -> KVal.ofText("")
        }

        private fun evalGv(args: List<KVal>): KVal {
            val key = args.getOrNull(0)?.text?.trim()?.lowercase().orEmpty()
            val fallback = args.getOrNull(1)?.text ?: ""
            val value = if (key.isEmpty()) fallback else (globals[key] ?: fallback)
            return KVal.ofText(value)
        }

        private fun evalCe(args: List<KVal>): KVal {
            val baseRaw = args.getOrNull(0)?.text ?: return KVal.ofText("#FFFFFFFF")
            val c1 = parseColorSafe(baseRaw) ?: 0xFFFFFFFF.toInt()
            val op = args.getOrNull(1)?.text?.lowercase() ?: return KVal.ofText(formatHexColor(c1))
            val a1 = (c1 ushr 24) and 0xFF
            val r1 = (c1 ushr 16) and 0xFF
            val g1 = (c1 ushr 8) and 0xFF
            val b1 = c1 and 0xFF
            return when (op) {
                "alpha" -> {
                    val pct = (args.getOrNull(2)?.num ?: 100.0).coerceIn(0.0, 100.0)
                    val a = ((pct / 100.0) * 255.0).roundToInt().coerceIn(0, 255)
                    KVal.ofText(formatHexColor((c1 and 0x00FFFFFF) or (a shl 24)))
                }
                "mix" -> {
                    val c2 = parseColorSafe(args.getOrNull(2)?.text ?: "") ?: 0xFFFFFFFF.toInt()
                    val t = ((args.getOrNull(3)?.num ?: 50.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
                    val a = (a1 + (((c2 ushr 24) and 0xFF) - a1) * t).roundToInt().coerceIn(0, 255)
                    val r = (r1 + (((c2 ushr 16) and 0xFF) - r1) * t).roundToInt().coerceIn(0, 255)
                    val g = (g1 + (((c2 ushr 8) and 0xFF) - g1) * t).roundToInt().coerceIn(0, 255)
                    val b = (b1 + ((c2 and 0xFF) - b1) * t).roundToInt().coerceIn(0, 255)
                    KVal.ofText(formatHexColor((a shl 24) or (r shl 16) or (g shl 8) or b))
                }
                "contrast" -> {
                    val lum = (0.2126 * r1 + 0.7152 * g1 + 0.0722 * b1) / 255.0
                    KVal.ofText(if (lum > 0.55) "#FF000000" else "#FFFFFFFF")
                }
                "lum" -> {
                    val lum = (0.2126 * r1 + 0.7152 * g1 + 0.0722 * b1) / 2.55
                    KVal.ofNum(lum)
                }
                else -> KVal.ofText(formatHexColor(c1))
            }
        }

        private fun formatHexColor(c: Int): String = String.format(Locale.US, "#%08X", c)

        private fun evalFl(args: List<KVal>): KVal {
            val start = (args.getOrNull(0)?.num ?: 1.0).roundToInt()
            val end = (args.getOrNull(1)?.num ?: 1.0).roundToInt()
            val step = (args.getOrNull(2)?.num ?: 1.0).roundToInt().let { if (it == 0) 1 else abs(it) }
            val body = args.getOrNull(3)?.text ?: return KVal.ofText("")
            val sep = args.getOrNull(4)?.text ?: ""
            val sb = StringBuilder()
            var count = 0
            var i = start
            val dir = if (end >= start) 1 else -1
            while ((if (dir > 0) i <= end else i >= end) && count < 64) {
                if (count > 0 && sep.isNotEmpty()) sb.append(sep)
                val out = runCatching {
                    Parser(body, env, now, globals, locals + ("i" to KVal.ofNum(i.toDouble()))).parseExpr().text
                }.getOrDefault("")
                sb.append(out)
                i += step * dir
                count++
            }
            return KVal.ofText(sb.toString())
        }

        private fun evalWi(param: String): KVal = when (param.lowercase()) {
            "temp" -> env.temp?.let { KVal.ofNum(it.toDouble()) } ?: KVal.ofText("--")
            "cond" -> KVal.ofText(Dynamic.weatherText(env.weatherCode))
            "city" -> KVal.ofText(env.city.ifBlank { "Local" })
            "code" -> KVal.ofNum(env.weatherCode.toDouble())
            "isday" -> KVal.ofBool(env.isDay)
            else -> KVal.ofText("")
        }

        private fun evalIf(args: List<KVal>): KVal {
            if (args.isEmpty()) return KVal.ofText("")
            var i = 0
            while (i + 1 < args.size) {
                if (args[i].isTruthy) return args[i + 1]
                i += 2
            }
            return if (i < args.size) args[i] else KVal.ofText("")
        }

        private fun evalTc(args: List<KVal>): KVal {
            val mode = args.getOrNull(0)?.text?.lowercase() ?: ""
            val txt = args.getOrNull(1)?.text ?: ""
            return when (mode) {
                "up", "upper" -> KVal.ofText(txt.uppercase(Locale.getDefault()))
                "low", "lower" -> KVal.ofText(txt.lowercase(Locale.getDefault()))
                "cap", "capitalize" -> {
                    val titled = txt.split(' ').joinToString(" ") { w ->
                        w.lowercase(Locale.getDefault()).replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                        }
                    }
                    KVal.ofText(titled)
                }
                "len" -> KVal.ofNum(txt.length.toDouble())
                "cut" -> {
                    val maxLen = args.getOrNull(2)?.num?.toInt()?.coerceAtLeast(0) ?: txt.length
                    KVal.ofText(txt.take(maxLen))
                }
                "ell" -> {
                    val maxLen = args.getOrNull(2)?.num?.toInt()?.coerceAtLeast(1) ?: txt.length
                    KVal.ofText(if (txt.length > maxLen) txt.take(maxLen - 1) + "…" else txt)
                }
                "split" -> {
                    val sep = args.getOrNull(2)?.text ?: " "
                    val idx = args.getOrNull(3)?.num?.toInt()?.coerceAtLeast(0) ?: 0
                    val parts = txt.split(sep)
                    KVal.ofText(parts.getOrElse(idx) { "" })
                }
                "lpad" -> {
                    val targetLen = args.getOrNull(2)?.num?.toInt()?.coerceIn(0, 64) ?: txt.length
                    val padChar = args.getOrNull(3)?.text?.firstOrNull() ?: '0'
                    KVal.ofText(txt.padStart(targetLen, padChar))
                }
                "rpad" -> {
                    val targetLen = args.getOrNull(2)?.num?.toInt()?.coerceIn(0, 64) ?: txt.length
                    val padChar = args.getOrNull(3)?.text?.firstOrNull() ?: ' '
                    KVal.ofText(txt.padEnd(targetLen, padChar))
                }
                "reg" -> {
                    val pat = args.getOrNull(2)?.text ?: ""
                    val repl = args.getOrNull(3)?.text ?: ""
                    KVal.ofText(runCatching { txt.replace(Regex(pat), repl) }.getOrDefault(txt))
                }
                "count" -> {
                    val sub = args.getOrNull(2)?.text ?: ""
                    if (sub.isEmpty()) KVal.ofNum(0.0) else KVal.ofNum(txt.windowed(sub.length) { it == sub }.count { it }.toDouble())
                }
                else -> KVal.ofText(txt)
            }
        }

        private fun evalMu(args: List<KVal>): KVal {
            val op = args.getOrNull(0)?.text?.lowercase() ?: return KVal.ofNum(0.0)
            val nums = args.drop(1).mapNotNull { it.num }
            if (nums.isEmpty()) return KVal.ofNum(0.0)
            return when (op) {
                "min" -> KVal.ofNum(nums.min())
                "max" -> KVal.ofNum(nums.max())
                "abs" -> KVal.ofNum(abs(nums[0]))
                "round" -> KVal.ofNum(nums[0].roundToLong().toDouble())
                "floor" -> KVal.ofNum(floor(nums[0]))
                "ceil" -> KVal.ofNum(ceil(nums[0]))
                "pow" -> KVal.ofNum(nums[0].pow(nums.getOrElse(1) { 1.0 }))
                "sqrt" -> KVal.ofNum(sqrt(nums[0].coerceAtLeast(0.0)))
                "sin" -> KVal.ofNum(sin(Math.toRadians(nums[0])))
                "cos" -> KVal.ofNum(cos(Math.toRadians(nums[0])))
                "clamp" -> {
                    val lo = nums.getOrElse(1) { 0.0 }
                    val hi = nums.getOrElse(2) { lo.coerceAtLeast(100.0) }
                    KVal.ofNum(nums[0].coerceIn(min(lo, hi), max(lo, hi)))
                }
                "rnd" -> {
                    val lo = nums[0].roundToInt()
                    val hi = nums.getOrElse(1) { 100.0 }.roundToInt()
                    val minV = min(lo, hi)
                    val maxV = max(lo, hi)
                    KVal.ofNum(Random.nextInt(minV, maxV + 1).toDouble())
                }
                else -> KVal.ofNum(nums[0])
            }
        }
    }
}
