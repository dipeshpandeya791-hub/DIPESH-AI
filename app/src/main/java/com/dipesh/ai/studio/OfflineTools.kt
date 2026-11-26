package com.dipesh.ai.studio

import java.security.SecureRandom
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.sqrt

/** Pure-Kotlin utilities that work with no internet. */
object Calc {
    /** Evaluates + - * / ^ % ( ) and sqrt(). Throws IllegalArgumentException on bad input. */
    fun eval(expr: String): Double {
        val p = Parser(expr.replace(" ", "").replace("×", "*").replace("÷", "/").replace(",", "."))
        val v = p.expr()
        if (p.pos != p.s.length) throw IllegalArgumentException("Unexpected '${p.s[p.pos]}'")
        if (v.isNaN() || v.isInfinite()) throw IllegalArgumentException("Not a finite number")
        return v
    }

    fun format(v: Double): String = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else "%.10g".format(v).trimEnd('0').trimEnd('.')

    private class Parser(val s: String) {
        var pos = 0
        fun peek() = if (pos < s.length) s[pos] else '\u0000'
        fun expr(): Double {
            var v = term()
            while (peek() == '+' || peek() == '-') { val op = s[pos++]; val r = term(); v = if (op == '+') v + r else v - r }
            return v
        }
        fun term(): Double {
            var v = power()
            while (peek() == '*' || peek() == '/') {
                val op = s[pos++]; val r = power()
                if (op == '/' && r == 0.0) throw IllegalArgumentException("Division by zero")
                v = if (op == '*') v * r else v / r
            }
            return v
        }
        fun power(): Double {
            val b = unary()
            if (peek() == '^') { pos++; return b.pow(power()) }
            return b
        }
        fun unary(): Double {
            if (peek() == '-') { pos++; return -unary() }
            if (peek() == '+') { pos++; return unary() }
            var v = atom()
            while (peek() == '%') { pos++; v /= 100.0 }
            return v
        }
        fun atom(): Double {
            if (peek() == '(') { pos++; val v = expr(); if (peek() != ')') throw IllegalArgumentException("Missing )"); pos++; return v }
            if (s.startsWith("sqrt(", pos)) {
                pos += 5; val v = expr(); if (peek() != ')') throw IllegalArgumentException("Missing )"); pos++
                if (v < 0) throw IllegalArgumentException("sqrt of a negative number"); return sqrt(v)
            }
            val start = pos
            while (peek().isDigit() || peek() == '.') pos++
            if (start == pos) throw IllegalArgumentException(if (pos >= s.length) "Incomplete expression" else "Unexpected '${s[pos]}'")
            return s.substring(start, pos).toDoubleOrNull() ?: throw IllegalArgumentException("Bad number")
        }
    }
}

object Units {
    private val toBase: Map<String, Pair<String, Double>> = buildMap {
        // length -> metres
        mapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "mi" to 1609.344, "yd" to 0.9144, "ft" to 0.3048, "in" to 0.0254)
            .forEach { (k, v) -> put(k, "length" to v) }
        // mass -> kilograms
        mapOf("kg" to 1.0, "g" to 0.001, "mg" to 1e-6, "lb" to 0.45359237, "oz" to 0.028349523125).forEach { (k, v) -> put(k, "mass" to v) }
        // volume -> litres
        mapOf("l" to 1.0, "ml" to 0.001, "gal" to 3.785411784, "cup" to 0.2365882365).forEach { (k, v) -> put(k, "volume" to v) }
        // speed -> m/s
        mapOf("m/s" to 1.0, "km/h" to 1000.0 / 3600.0, "mph" to 0.44704).forEach { (k, v) -> put(k, "speed" to v) }
        // data -> bytes
        mapOf("b" to 1.0, "kb" to 1024.0, "mb" to 1024.0 * 1024, "gb" to 1024.0 * 1024 * 1024).forEach { (k, v) -> put(k, "data" to v) }
    }
    private val temps = setOf("c", "f", "k")

    val supported: String = (toBase.keys + temps).sorted().joinToString(", ")

    /** Parses "10 km to mi" style text. */
    fun convertText(q: String): String {
        val m = Regex("(?i)^\\s*(-?[0-9]+(?:[.,][0-9]+)?)\\s*([a-z/]+)\\s*(?:to|in|->)\\s*([a-z/]+)\\s*$").find(q)
            ?: throw IllegalArgumentException("Write it like: 10 km to mi")
        val v = m.groupValues[1].replace(',', '.').toDouble()
        val from = m.groupValues[2].lowercase(); val to = m.groupValues[3].lowercase()
        return "${Calc.format(v)} $from = ${Calc.format(convert(v, from, to))} $to"
    }

    fun convert(v: Double, from: String, to: String): Double {
        if (from in temps && to in temps) {
            val c = when (from) { "c" -> v; "f" -> (v - 32) * 5 / 9; else -> v - 273.15 }
            return when (to) { "c" -> c; "f" -> c * 9 / 5 + 32; else -> c + 273.15 }
        }
        val a = toBase[from] ?: throw IllegalArgumentException("Unknown unit '$from'. Supported: $supported")
        val b = toBase[to] ?: throw IllegalArgumentException("Unknown unit '$to'. Supported: $supported")
        if (a.first != b.first) throw IllegalArgumentException("Can't convert ${a.first} to ${b.first}")
        return v * a.second / b.second
    }
}

object PasswordGen {
    fun generate(length: Int, upper: Boolean = true, digits: Boolean = true, symbols: Boolean = true, rnd: SecureRandom = SecureRandom()): String {
        val len = length.coerceIn(8, 64)
        val pools = mutableListOf("abcdefghijklmnopqrstuvwxyz")
        if (upper) pools.add("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        if (digits) pools.add("0123456789")
        if (symbols) pools.add("!@#%^&*()-_=+[]{}?")
        val all = pools.joinToString("")
        val chars = pools.map { it[rnd.nextInt(it.length)] }.toMutableList() // at least one of each chosen kind
        while (chars.size < len) chars.add(all[rnd.nextInt(all.length)])
        chars.shuffle(rnd)
        return chars.joinToString("")
    }
}

object TextTools {
    fun stats(t: String): String {
        val words = t.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val lines = if (t.isEmpty()) 0 else t.lines().size
        return "Characters: ${t.length}\nWords: $words\nLines: $lines\nReading time: about ${maxOf(1, words / 200)} min"
    }
    fun base64(t: String, decode: Boolean): String =
        if (decode) String(java.util.Base64.getDecoder().decode(t.trim()), Charsets.UTF_8) else java.util.Base64.getEncoder().encodeToString(t.toByteArray(Charsets.UTF_8))
}

object DateMath {
    /** "2026-12-25" or "2026-01-01 to 2026-12-25" */
    fun describe(q: String, today: LocalDate = LocalDate.now()): String {
        val dates = Regex("\\d{4}-\\d{2}-\\d{2}").findAll(q).map { LocalDate.parse(it.value) }.toList()
        return when (dates.size) {
            1 -> { val d = ChronoUnit.DAYS.between(today, dates[0]); if (d >= 0) "$d days from today (${dates[0]})" else "${-d} days ago (${dates[0]})" }
            2 -> "${ChronoUnit.DAYS.between(dates[0], dates[1])} days between ${dates[0]} and ${dates[1]}"
            else -> throw IllegalArgumentException("Write one date (2026-12-25) or two dates")
        }
    }
}
