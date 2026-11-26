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
