package com.dipesh.ai.studio

/** Tiny, honest forecasting: an ordinary least-squares line through recent values, never below zero. */
object Forecast {
    fun linear(values: List<Double>, ahead: Int): List<Double> {
        if (values.isEmpty() || ahead <= 0) return emptyList()
        if (values.size == 1) return List(ahead) { values[0] }
        val n = values.size
        val xm = (n - 1) / 2.0
        val ym = values.average()
        var num = 0.0; var den = 0.0
        values.forEachIndexed { i, y -> num += (i - xm) * (y - ym); den += (i - xm) * (i - xm) }
        val slope = if (den == 0.0) 0.0 else num / den
        val intercept = ym - slope * xm
        return (0 until ahead).map { k -> (intercept + slope * (n + k)).coerceAtLeast(0.0) }
    }

    fun trend(values: List<Double>): String {
        if (values.size < 2) return "not enough data yet"
        val f = linear(values, 1)[0]; val last = values.takeLast(3).average()
        return when {
            f > last * 1.1 + 0.2 -> "rising"
            f < last * 0.9 - 0.2 -> "falling"
            else -> "steady"
        }
    }
}
