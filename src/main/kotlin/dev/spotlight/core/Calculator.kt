package dev.spotlight.core

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.pow

data class Calculation(val value: Double, val display: String, val stacks: String)

object Calculator {
    
    fun evaluate(input: String): Calculation? {
        if (input.isBlank() || input.length > 512) return null
        return try {
            val value = Parser(input).parse().let { if (it == 0.0) 0.0 else it }
            val quotient = (value / 64).toLong()
            val surplus = value - quotient * 64.0
            Calculation(value, format(value), "$quotient stacks + ${format(surplus)}")
        } catch (_: IllegalArgumentException) { null }
    }

    private fun format(value: Double) = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private class Parser(val text: String) {
        var position = 0
        var depth = 0
        fun parse(): Double {
            val result = expression()
            space()
            require(position == text.length)
            return result
        }
        fun space() { while (position < text.length && text[position].isWhitespace()) position++ }
        fun take(char: Char): Boolean {
            space()
            return if (position < text.length && text[position] == char) { position++; true } else false
        }
        fun bounded(value: Double): Double {
            require(value.isFinite() && abs(value) <= 1e15) { "Arithmetic out of bounds" }
            return value
        }
        fun expression(): Double {
            var value = product()
            while (true) value = when {
                take('+') -> bounded(value + product())
                take('-') -> bounded(value - product())
                else -> return value
            }
        }
        fun product(): Double {
            var value = unary()
            while (true) value = when {
                take('*') -> bounded(value * unary())
                take('/') -> { val right = unary(); require(right != 0.0); bounded(value / right) }
                take('%') -> { val right = unary(); require(right != 0.0); bounded(value % right) }
                else -> return value
            }
        }
        fun unary(): Double {
            require(++depth <= 64) { "Expression too deep" }
            try {
                return when {
                    take('+') -> unary()
                    take('-') -> -unary()
                    else -> {
                        val value = atom()
                        if (take('^')) bounded(value.pow(unary())) else value
                    }
                }
            } finally { depth-- }
        }
        fun atom(): Double {
            if (take('(')) { val value = expression(); require(take(')')); return value }
            space()
            val start = position
            while (position < text.length && (text[position].isDigit() || text[position] == '.')) position++
            if (position < text.length && text[position] in "eE") {
                position++
                if (position < text.length && text[position] in "+-") position++
                while (position < text.length && text[position].isDigit()) position++
            }
            require(position > start)
            return bounded(text.substring(start, position).toDoubleOrNull() ?: throw IllegalArgumentException("Invalid number"))
        }
    }
}
