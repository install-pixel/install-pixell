package com.blacklaunch.launcher.global

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Калькулятор у пошуку: «25*4», «12,5 + 7», «2^10», «(3+4)×2», «15%» (відсоток від числа:
 * «200*15%» = 30). Повертає null, якщо це не вираз — тоді нічого не показуємо.
 */
object Calculator {
    private val allowed = Regex("^[0-9+\\-*/×÷x^%().,\\s]+$")

    fun evaluate(input: String): String? {
        val text = input.trim()
        // Хоча б один оператор між числами — інакше «2026» чи «7,5» не є обчисленням.
        if (text.isEmpty() || !allowed.matches(text) || !text.any { it in "+-*/×÷x^%" } || !text.any { it.isDigit() }) {
            return null
        }
        return try {
            val parser = Parser(normalize(text))
            val value = parser.parseExpression()
            if (!parser.atEnd()) null else format(value)
        } catch (e: ArithmeticException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun normalize(text: String) = text.replace('×', '*').replace('x', '*').replace('÷', '/')
        .replace(',', '.').replace(" ", "")

    private fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        return if (rounded.scale() < 0) rounded.setScale(0).toPlainString() else rounded.toPlainString()
    }

    /** Рекурсивний розбір: вираз = доданки, доданок = множники, множник = степінь. */
    private class Parser(private val s: String) {
        private var pos = 0

        fun atEnd() = pos >= s.length

        fun parseExpression(): BigDecimal {
            var value = parseTerm()
            while (!atEnd() && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val right = parseTerm(percentBase = value)
                value = if (op == '+') value + right else value - right
            }
            return value
        }

        private fun parseTerm(percentBase: BigDecimal? = null): BigDecimal {
            var value = parsePower(percentBase)
            while (!atEnd() && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val right = parsePower(null, multiplier = true)
                value = if (op == '*') {
                    value * right
                } else {
                    if (right.signum() == 0) throw ArithmeticException("division by zero")
                    value.divide(right, MathContext.DECIMAL64)
                }
            }
            return value
        }

        private fun parsePower(percentBase: BigDecimal?, multiplier: Boolean = false): BigDecimal {
            val base = parseUnary(percentBase, multiplier)
            if (!atEnd() && s[pos] == '^') {
                pos++
                val exponent = parsePower(null)
                val n = exponent.intValueExact()
                if (n !in -100..100) throw ArithmeticException("exponent too large")
                return if (n >= 0) base.pow(n) else BigDecimal.ONE.divide(base.pow(-n), MathContext.DECIMAL64)
            }
            return base
        }

        private fun parseUnary(percentBase: BigDecimal?, multiplier: Boolean): BigDecimal {
            if (!atEnd() && s[pos] == '-') {
                pos++
                return parseUnary(percentBase, multiplier).negate()
            }
            var value = parsePrimary()
            if (!atEnd() && s[pos] == '%') {
                pos++
                // «200 + 10%» = 220; «200 * 15%» = 30; «15%» = 0.15.
                val fraction = value.divide(HUNDRED, MathContext.DECIMAL64)
                value = if (percentBase != null) percentBase * fraction else fraction
            }
            return value
        }

        private fun parsePrimary(): BigDecimal {
            if (atEnd()) throw IllegalArgumentException("unexpected end")
            if (s[pos] == '(') {
                pos++
                val value = parseExpression()
                if (atEnd() || s[pos] != ')') throw IllegalArgumentException("missing )")
                pos++
                return value
            }
            val start = pos
            while (!atEnd() && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("number expected at $pos")
            return BigDecimal(s.substring(start, pos))
        }

        companion object {
            private val HUNDRED = BigDecimal(100)
        }
    }
}
