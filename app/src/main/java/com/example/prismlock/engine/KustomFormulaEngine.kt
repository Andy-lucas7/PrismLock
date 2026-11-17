package com.example.prismlock.engine

import android.os.Build
import com.example.prismlock.data.Env
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

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
            label = "Condicionais IF",
            items = listOf(
                FormulaSnippet("Alerta Bateria", "\$if(bi(level) < 20, \"Bateria Baixa\", \"Normal\")\$"),
                FormulaSnippet("Se Carregando", "\$if(bi(status) = \"Carregando\", \"⚡ Carregando\", bi(level) + \"%\")\$"),
                FormulaSnippet("Dia MAIÚSCULO", "\$tc(up, df(EEEE))\$"),
                FormulaSnippet("Clima minúsculo", "\$tc(low, wi(cond))\$"),
                FormulaSnippet("Capitalizar", "\$tc(cap, df(EEEE))\$")
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
        "{ss}" in text || ("\$df(" in text && "ss" in text)

    fun evalDollarBlocks(text: String, env: Env, now: LocalDateTime): String {
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
                val evaluated = runCatching { Parser(expr, env, now).parseExpr().text }
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

    fun evalNumeric(expr: String, env: Env, now: LocalDateTime): Double? {
        val trimmed = expr.trim()
        if ('$' in trimmed) {
            val evaluated = evalDollarBlocks(trimmed, env, now).trim().removeSuffix("%").trim()
            evaluated.toDoubleOrNull()?.let { return it }
            runCatching { Expr(evaluated, Dynamic.vars(env, now)).parse() }.getOrNull()?.let { return it }
        }
        val inner = if (trimmed.startsWith("$") && trimmed.endsWith("$") && trimmed.length >= 2) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
        return runCatching {
            val v = Parser(inner, env, now).parseExpr()
            v.num ?: v.text.toDoubleOrNull()
        }.getOrNull()
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
        private val now: LocalDateTime
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
            "bi" -> evalBi(args.getOrNull(0)?.text ?: "level")
            "si" -> evalSi(args.getOrNull(0)?.text ?: "model")
            "wi" -> evalWi(args.getOrNull(0)?.text ?: "temp")
            "if" -> evalIf(args)
            "tc" -> evalTc(args)
            "mu" -> evalMu(args)
            else -> KVal.ofText("")
        }

        private fun evalDf(pattern: String): KVal {
            if (pattern.isEmpty()) return KVal.ofText("")
            val fmt = fmtCache.getOrPut(pattern) {
                runCatching { DateTimeFormatter.ofPattern(pattern, Locale.getDefault()) }.getOrNull()
            }
            val formatted = fmt?.let { runCatching { it.format(now) }.getOrNull() } ?: pattern
            return KVal.ofText(formatted)
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
            "locked" -> KVal.ofBool(env.locked)
            "steps" -> KVal.ofNum(env.steps.toDouble())
            else -> KVal.ofText("")
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
                else -> KVal.ofNum(nums[0])
            }
        }
    }
}
