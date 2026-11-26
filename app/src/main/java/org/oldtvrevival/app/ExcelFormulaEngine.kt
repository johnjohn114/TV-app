package org.oldtvrevival.app

/**
 * Lightweight formula evaluator for the everyday functions supported by OldTV Revival.
 * Supported: SUM, AVERAGE, MAX, MIN, COUNT, IF; arithmetic, comparisons, cell references and ranges.
 */
object ExcelFormulaEngine {
    fun evaluate(formula: String, grid: List<List<String>>): String {
        if (!formula.trimStart().startsWith("=")) return formula
        return try {
            val parser = Parser(formula.trim().drop(1), grid)
            val value = parser.parseExpression()
            if (!parser.atEnd()) error("公式格式不正確")
            format(value)
        } catch (e: Exception) {
            "#公式錯誤"
        }
    }

    private fun format(value: Any?): String = when (value) {
        null -> ""
        is Boolean -> if (value) "TRUE" else "FALSE"
        is Double -> if (!value.isFinite()) "#數值錯誤" else {
            if (value == value.toLong().toDouble()) value.toLong().toString()
            else value.toString()
        }
        else -> value.toString()
    }

    private class Parser(private val source: String, private val grid: List<List<String>>) {
        private val tokens = Regex("""\s*(>=|<=|<>|[()+*/,:=<>-]|"(?:[^"]|"")*"|(?:\d+(?:\.\d*)?|\.\d+)|[A-Za-z_][A-Za-z_0-9]*)""")
            .findAll(source).map { it.groupValues[1] }.toList()
        private var pos = 0
        fun atEnd() = pos >= tokens.size
        private fun peek() = tokens.getOrNull(pos) ?: ""
        private fun take(): String = tokens.getOrNull(pos++) ?: error("公式不完整")
        private fun accept(token: String): Boolean {
            if (peek() == token) { pos++; return true }
            return false
        }

        fun parseExpression(): Any? = comparison()

        private fun comparison(): Any? {
            val left = additive()
            val op = peek()
            if (op !in listOf("=", "<>", ">", "<", ">=", "<=")) return left
            take()
            val right = additive()
            val a = numberOrNull(left)
            val b = numberOrNull(right)
            val cmp = if (a != null && b != null) a.compareTo(b)
                else left.toString().compareTo(right.toString(), ignoreCase = true)
            return when (op) {
                "=" -> cmp == 0
                "<>" -> cmp != 0
                ">" -> cmp > 0
                "<" -> cmp < 0
                ">=" -> cmp >= 0
                else -> cmp <= 0
            }
        }

        private fun additive(): Any? {
            var value = multiplicative()
            while (peek() == "+" || peek() == "-") {
                val op = take()
                val right = multiplicative()
                value = if (op == "+") asNumber(value) + asNumber(right)
                    else asNumber(value) - asNumber(right)
            }
            return value
        }

        private fun multiplicative(): Any? {
            var value = unary()
            while (peek() == "*" || peek() == "/") {
                val op = take()
                val right = unary()
                val divisor = asNumber(right)
                if (op == "/" && divisor == 0.0) error("除數不可為零")
                value = if (op == "*") asNumber(value) * divisor else asNumber(value) / divisor
            }
            return value
        }

        private fun unary(): Any? {
            if (accept("-")) return -asNumber(unary())
            if (accept("+")) return asNumber(unary())
            return primary()
        }

        private fun primary(): Any? {
            if (accept("(")) {
                val value = parseExpression()
                require(accept(")")) { "缺少右括號" }
                return value
            }
            val token = take()
            if (token.startsWith("\"")) return token.substring(1, token.length - 1).replace("\"\"", "\"")
            token.toDoubleOrNull()?.let { return it }
            if (token.equals("TRUE", true)) return true
            if (token.equals("FALSE", true)) return false
            if (peek() == "(") return function(token)
            if (token.matches(Regex("[A-Za-z]+[0-9]+"))) {
                if (accept(":")) {
                    val end = take()
                    require(end.matches(Regex("[A-Za-z]+[0-9]+"))) { "範圍格式錯誤" }
                    return range(token, end)
                }
                return cell(token)
            }
            error("不支援的項目：$token")
        }

        private fun function(name: String): Any? {
            take() // (
            val args = mutableListOf<Any?>()
            if (!accept(")")) {
                do { args.add(parseExpression()) } while (accept(","))
                require(accept(")")) { "缺少右括號" }
            }
            val flat = args.flatMap { if (it is List<*>) it else listOf(it) }
            val nums = flat.mapNotNull { numberOrNull(it) }
            return when (name.uppercase()) {
                "SUM" -> nums.sum()
                "AVERAGE" -> if (nums.isEmpty()) error("沒有數值") else nums.average()
                "MAX" -> nums.maxOrNull() ?: error("沒有數值")
                "MIN" -> nums.minOrNull() ?: error("沒有數值")
                "COUNT" -> nums.size.toDouble()
                "IF" -> {
                    require(args.size == 3) { "IF 需要三個參數" }
                    if (truthy(args[0])) args[1] else args[2]
                }
                else -> error("不支援的函數：$name")
            }
        }

        private fun cell(ref: String, depth: Int = 0): Any? {
            require(depth < 32) { "公式循環參照" }
            val match = Regex("([A-Za-z]+)([0-9]+)").matchEntire(ref) ?: error("儲存格參照錯誤")
            var col = 0
            for (ch in match.groupValues[1].uppercase()) col = col * 26 + (ch - 'A' + 1)
            val row = match.groupValues[2].toInt() - 1
            val raw = grid.getOrNull(row)?.getOrNull(col - 1).orEmpty().trim()
            if (raw.isEmpty()) return 0.0
            if (raw.startsWith("=")) return evaluate(raw, grid).toDoubleOrNull() ?: evaluate(raw, grid)
            return raw.toDoubleOrNull() ?: raw
        }

        private fun range(start: String, end: String): List<Any?> {
            val a = Regex("([A-Za-z]+)([0-9]+)").matchEntire(start) ?: error("範圍錯誤")
            val b = Regex("([A-Za-z]+)([0-9]+)").matchEntire(end) ?: error("範圍錯誤")
            fun col(s: String): Int { var n = 0; for (c in s.uppercase()) n = n * 26 + (c - 'A' + 1); return n }
            val c1 = col(a.groupValues[1]); val c2 = col(b.groupValues[1])
            val r1 = a.groupValues[2].toInt(); val r2 = b.groupValues[2].toInt()
            require(kotlin.math.abs(c2 - c1) <= 255 && kotlin.math.abs(r2 - r1) <= 999) { "範圍過大" }
            val result = mutableListOf<Any?>()
            for (r in minOf(r1, r2)..maxOf(r1, r2)) {
                for (c in minOf(c1, c2)..maxOf(c1, c2)) {
                    result.add(cell(columnName(c) + r))
                }
            }
            return result
        }

        private fun columnName(value: Int): String {
            var n = value; val out = StringBuilder()
            while (n > 0) { out.append(('A'.code + (n - 1) % 26).toChar()); n = (n - 1) / 26 }
            return out.reverse().toString()
        }

        private fun numberOrNull(value: Any?): Double? = when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            else -> value?.toString()?.toDoubleOrNull()
        }
        private fun asNumber(value: Any?): Double = numberOrNull(value) ?: error("需要數值")
        private fun truthy(value: Any?): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            else -> !value.toString().isNullOrEmpty() && value.toString() != "0"
        }
    }
}
