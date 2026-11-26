package org.oldtvrevival.app

object FormulaEngine {
    fun evaluate(formula: String, cellValue: (String) -> String): String {
        return try {
            val source = formula.trim().removePrefix("=")
            val parser = Parser(source, cellValue)
            val value = parser.parse()
            format(value)
        } catch (e: Exception) {
            if (e.message == "#CYCLE!") "#循環參照" else "#公式錯誤"
        }
    }

    private fun format(value: Any?): String = when (value) {
        null -> ""
        is Boolean -> if (value) "TRUE" else "FALSE"
        is Number -> {
            val d = value.toDouble()
            if (!d.isFinite()) "#VALUE!" else if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        }
        else -> value.toString()
    }

    private class Parser(private val source: String, private val cellValue: (String) -> String) {
        private val tokens = tokenize(source)
        private var pos = 0
        fun parse(): Any? {
            val result = comparison()
            if (pos != tokens.size) error("Unexpected token")
            return result
        }
        private fun peek() = tokens.getOrNull(pos) ?: ""
        private fun take() = tokens.getOrNull(pos++) ?: error("Unexpected end")
        private fun match(value: String): Boolean = if (peek().equals(value, true)) { pos++; true } else false

        private fun comparison(): Any? {
            var left = addition()
            while (peek() in listOf("=", "<>", ">", "<", ">=", "<=")) {
                val op = take(); val right = addition()
                val a = numberOrNull(left); val b = numberOrNull(right)
                val cmp = if (a != null && b != null) a.compareTo(b) else left.toString().compareTo(right.toString(), true)
                left = when (op) { "=" -> cmp == 0; "<>" -> cmp != 0; ">" -> cmp > 0; "<" -> cmp < 0; ">=" -> cmp >= 0; else -> cmp <= 0 }
            }
            return left
        }
        private fun addition(): Any? {
            var left = multiplication()
            while (peek() == "+" || peek() == "-") {
                val op = take(); val right = multiplication()
                left = if (op == "+") number(left) + number(right) else number(left) - number(right)
            }
            return left
        }
        private fun multiplication(): Any? {
            var left = unary()
            while (peek() == "*" || peek() == "/") {
                val op = take(); val right = unary()
                if (op == "/" && number(right) == 0.0) error("Division by zero")
                left = if (op == "*") number(left) * number(right) else number(left) / number(right)
            }
            return left
        }
        private fun unary(): Any? = when {
            match("+") -> number(unary())
            match("-") -> -number(unary())
            else -> primary()
        }
        private fun primary(): Any? {
            if (match("(")) { val v = comparison(); if (!match(")")) error("Missing )"); return v }
            val token = take()
            if (token.startsWith("\"")) return token.substring(1, token.length - 1)
            token.toDoubleOrNull()?.let { return it }
            if (token.equals("TRUE", true)) return true
            if (token.equals("FALSE", true)) return false
            if (peek() == "(") {
                pos++
                val args = mutableListOf<Any?>()
                if (!match(")")) {
                    do { args.add(comparison()) } while (match(",") || match(";"))
                    if (!match(")")) error("Missing )")
                }
                return function(token.uppercase(), args)
            }
            if (token.matches(Regex("[A-Za-z]+[0-9]+"))) {
                val first = cellValue(token.uppercase())
                if (match(":")) {
                    val end = take().uppercase()
                    if (!end.matches(Regex("[A-Z]+[0-9]+"))) error("Invalid range")
                    return expandRange(token.uppercase(), end).map { cellValue(it) }
                }
                return first.toDoubleOrNull() ?: first
            }
            error("Unknown token")
        }
        private fun function(name: String, args: List<Any?>): Any? {
            val values = args.flatMap { if (it is List<*>) it else listOf(it) }
            return when (name) {
                "SUM" -> values.mapNotNull { numberOrNull(it) }.sum()
                "AVERAGE" -> { val nums = values.mapNotNull { numberOrNull(it) }; if (nums.isEmpty()) 0.0 else nums.average() }
                "MAX" -> values.mapNotNull { numberOrNull(it) }.maxOrNull() ?: 0.0
                "MIN" -> values.mapNotNull { numberOrNull(it) }.minOrNull() ?: 0.0
                "COUNT" -> values.count { numberOrNull(it) != null }.toDouble()
                "IF" -> { if (args.size != 3) error("IF needs 3 arguments"); if (truthy(args[0])) args[1] else args[2] }
                else -> error("Unsupported function")
            }
        }
        private fun expandRange(start: String, end: String): List<String> {
            val a = Regex("([A-Z]+)([0-9]+)").matchEntire(start) ?: error("Bad cell")
            val b = Regex("([A-Z]+)([0-9]+)").matchEntire(end) ?: error("Bad cell")
            fun col(s: String): Int { var n = 0; s.forEach { n = n * 26 + (it - 'A' + 1) }; return n }
            fun colName(n0: Int): String { var n = n0; val out = StringBuilder(); while (n > 0) { out.append(('A'.code + (n - 1) % 26).toChar()); n = (n - 1) / 26 }; return out.reverse().toString() }
            val c1 = col(a.groupValues[1]); val c2 = col(b.groupValues[1])
            val r1 = a.groupValues[2].toInt(); val r2 = b.groupValues[2].toInt()
            if (kotlin.math.abs(c2 - c1) > 255 || kotlin.math.abs(r2 - r1) > 9999) error("Range too large")
            return buildList { for (r in minOf(r1, r2)..maxOf(r1, r2)) for (c in minOf(c1, c2)..maxOf(c1, c2)) add(colName(c) + r) }
        }
        private fun number(value: Any?): Double = numberOrNull(value) ?: 0.0
        private fun numberOrNull(value: Any?): Double? = when (value) { is Number -> value.toDouble(); is Boolean -> if (value) 1.0 else 0.0; else -> value?.toString()?.toDoubleOrNull() }
        private fun truthy(value: Any?): Boolean = when (value) { is Boolean -> value; is Number -> value.toDouble() != 0.0; null -> false; else -> value.toString().isNotEmpty() }
        private fun tokenize(input: String): List<String> {
            val out = mutableListOf<String>(); var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c.isWhitespace()) { i++; continue }
                if (c == '"') { val start = i++; while (i < input.length && input[i] != '"') i++; if (i >= input.length) error("Unclosed string"); i++; out.add(input.substring(start, i)); continue }
                if (c.isDigit() || c == '.') { val start = i++; while (i < input.length && (input[i].isDigit() || input[i] == '.')) i++; out.add(input.substring(start, i)); continue }
                if (c.isLetter() || c == '_') { val start = i++; while (i < input.length && input[i].isLetterOrDigit()) i++; out.add(input.substring(start, i)); continue }
                if (i + 1 < input.length && input.substring(i, i + 2) in listOf(">=", "<=", "<>")) { out.add(input.substring(i, i + 2)); i += 2; continue }
                if (c in charArrayOf('+', '-', '*', '/', '(', ')', ',', ';', ':', '=', '>', '<')) { out.add(c.toString()); i++; continue }
                error("Invalid character")
            }
            return out
        }
    }
}
