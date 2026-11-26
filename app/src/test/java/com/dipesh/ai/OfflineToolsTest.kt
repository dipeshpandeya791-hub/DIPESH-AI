package com.dipesh.ai

import com.dipesh.ai.studio.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OfflineToolsTest {
    @Test fun calculator() {
        assertEquals(14.0, Calc.eval("2+3*4"), 1e-9)
        assertEquals(20.0, Calc.eval("(2+3)*4"), 1e-9)
        assertEquals(512.0, Calc.eval("2^3^2"), 1e-9)
        assertEquals(-3.0, Calc.eval("-(1+2)"), 1e-9)
        assertEquals(5.0, Calc.eval("sqrt(25)"), 1e-9)
        assertEquals(1.0, Calc.eval("50%*2"), 1e-9)
        assertEquals("0.3", Calc.format(Calc.eval("0.1+0.2")))
        assertEquals("14", Calc.format(14.0))
        listOf("1/0", "2+", "(1+2", "abc", "sqrt(-1)", "").forEach { assertTrue("'$it'", runCatching { Calc.eval(it) }.isFailure) }
    }

    @Test fun unitConversion() {
        assertEquals("10 km = 6.21371192 mi".substringBefore("="), Units.convertText("10 km to mi").substringBefore("="))
        assertEquals(1000.0, Units.convert(1.0, "km", "m"), 1e-9)
        assertEquals(212.0, Units.convert(100.0, "c", "f"), 1e-9)
        assertEquals(273.15, Units.convert(0.0, "c", "k"), 1e-9)
        assertEquals(2.2046, Units.convert(1.0, "kg", "lb"), 1e-3)
        assertTrue(runCatching { Units.convert(1.0, "kg", "m") }.isFailure)
        assertTrue(runCatching { Units.convertText("hello") }.isFailure)
    }

    @Test fun passwords() {
        val p = PasswordGen.generate(20)
        assertEquals(20, p.length)
        assertTrue(p.any { it.isLowerCase() } && p.any { it.isUpperCase() } && p.any { it.isDigit() } && p.any { !it.isLetterOrDigit() })
        assertEquals(8, PasswordGen.generate(2).length)
        assertTrue(PasswordGen.generate(30, symbols = false).all { it.isLetterOrDigit() })
        assertNotEquals(PasswordGen.generate(20), PasswordGen.generate(20))
    }

    @Test fun textAndDates() {
        assertTrue(TextTools.stats("one two three").contains("Words: 3"))
        assertEquals("aGk=", TextTools.base64("hi", false)); assertEquals("hi", TextTools.base64("aGk=", true))
        val today = LocalDate.of(2026, 11, 26)
        assertEquals("29 days from today (2026-12-25)", DateMath.describe("2026-12-25", today))
        assertEquals("4 days between 2026-01-01 and 2026-01-05", DateMath.describe("2026-01-01 to 2026-01-05", today))
        assertTrue(runCatching { DateMath.describe("tomorrow", today) }.isFailure)
    }
}
