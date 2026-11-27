package com.dipesh.ai

import com.dipesh.ai.studio.Forecast
import org.junit.Assert.*
import org.junit.Test

class HoloTest {
    @Test fun forecastContinuesLine() {
        val f = Forecast.linear(listOf(1.0, 2.0, 3.0, 4.0), 2)
        assertEquals(5.0, f[0], 1e-9); assertEquals(6.0, f[1], 1e-9)
    }
    @Test fun forecastNeverNegative() {
        assertTrue(Forecast.linear(listOf(5.0, 3.0, 1.0), 3).all { it >= 0.0 })
    }
    @Test fun forecastEdgeCases() {
        assertTrue(Forecast.linear(emptyList(), 3).isEmpty())
        assertEquals(listOf(7.0, 7.0), Forecast.linear(listOf(7.0), 2))
    }
    @Test fun trendWords() {
        assertEquals("rising", Forecast.trend(listOf(1.0, 3.0, 5.0, 8.0, 12.0)))
        assertEquals("falling", Forecast.trend(listOf(12.0, 9.0, 6.0, 3.0, 1.0)))
        assertEquals("steady", Forecast.trend(listOf(4.0, 4.0, 4.0, 4.0)))
    }
    @Test fun catalogRoutesValid() {
        FeatureCatalog.all.forEach { f -> if (f.route != null) assertTrue(f.title, f.route in FeatureCatalog.routes) }
        assertTrue(FeatureCatalog.categories.size >= 5)
        assertTrue(FeatureCatalog.all.size >= 20)
    }
    @Test fun showFeaturesCommand() {
        assertEquals(RuleBased.Intent.ShowFeatures, RuleBased.parse("show features"))
        assertEquals(RuleBased.Intent.ShowFeatures, RuleBased.parse("Hey, show me your features please"))
        assertEquals(RuleBased.Intent.ShowFeatures, RuleBased.parse("what can you do"))
        assertEquals(RuleBased.Intent.HideFeatures, RuleBased.parse("close features"))
    }
}

class PersonaTest {
    @Test fun moods() {
        assertEquals(Persona.Mood.SAD, Persona.mood("I'm sad today"))
        assertEquals(Persona.Mood.SAD, Persona.mood("मन भारी छ"))
        assertEquals(Persona.Mood.STRESSED, Persona.mood("I am so stressed about exams"))
        assertEquals(Persona.Mood.TIRED, Persona.mood("I'm tired"))
        assertEquals(Persona.Mood.ANGRY, Persona.mood("this is so frustrating, I'm angry"))
        assertEquals(Persona.Mood.HAPPY, Persona.mood("Great news, I passed!"))
        assertEquals(Persona.Mood.NEUTRAL, Persona.mood("what time is it"))
        assertTrue(Persona.moodBlock(Persona.Mood.NEUTRAL).isEmpty())
        assertTrue(Persona.moodBlock(Persona.Mood.SAD).contains("sad"))
    }

    @Test fun modes() {
        assertEquals(Persona.Mode.BOSS, Persona.detect("Boss mode"))
        assertEquals(Persona.Mode.BOSS, Persona.detect("be brief"))
        assertEquals(Persona.Mode.STUDY, Persona.detect("switch to study mode please"))
        assertEquals(Persona.Mode.STUDY, Persona.detect("Help me learn"))
        assertEquals(Persona.Mode.CREATOR, Persona.detect("let's create"))
        assertEquals(Persona.Mode.CREATOR, Persona.detect("activate creator"))
        assertEquals(Persona.Mode.NORMAL, Persona.detect("normal mode"))
        assertNull(Persona.detect("what is the weather"))
    }
    @Test fun script() {
        assertTrue(Persona.isDevanagari("नमस्ते, तपाईंलाई कस्तो छ"))
        assertFalse(Persona.isDevanagari("hello there"))
        assertFalse(Persona.isDevanagari("   "))
    }
    @Test fun promptBlocks() {
        assertTrue(Persona.core.contains("AUTO LANGUAGE"))
        assertTrue(Persona.modeBlock(Persona.Mode.BOSS).contains("Boss"))
        assertEquals("", Persona.modeBlock(Persona.Mode.NORMAL))
    }
}

class LocalShellTest {
    @Test fun needsTermux() {
        assertTrue(LocalShell.needsTermux("python3 a.py"))
        assertTrue(LocalShell.needsTermux("cd x && git status"))
        assertFalse(LocalShell.needsTermux("ls -la"))
    }
    @Test fun formatHint() {
        assertTrue(LocalShell.format(127, "", "sh: python: not found").contains("Termux"))
        assertEquals("exit code 0\nstdout:\nhi", LocalShell.format(0, "hi", ""))
    }
}
