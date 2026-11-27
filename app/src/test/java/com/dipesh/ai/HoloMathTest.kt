package com.dipesh.ai

import com.dipesh.ai.holo.HoloMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class HoloMathTest {
    private val o = FloatArray(4)

    @Test fun originProjectsToCentre() {
        HoloMath.project(0f, 0f, 0f, 0.3f, 0.2f, o)
        assertEquals(0f, o[0], 1e-5f); assertEquals(0f, o[1], 1e-5f); assertEquals(1f, o[2], 1e-5f)
    }

    @Test fun nearerPointsAreBigger() {
        HoloMath.project(1f, 0f, -2f, 0f, 0f, o); val near = o[2]
        HoloMath.project(1f, 0f, 2f, 0f, 0f, o); val far = o[2]
        assertTrue(near > 1f && far < 1f && near > far)
    }

    @Test fun yawQuarterTurnMovesXAxisIntoDepth() {
        HoloMath.project(1f, 0f, 0f, (PI / 2).toFloat(), 0f, o)
        assertEquals(0f, o[0], 1e-4f)
        assertEquals(-1f, o[3], 1e-4f) // toward the viewer
    }

    @Test fun projectionNeverDivergesBehindCamera() {
        HoloMath.project(1f, 1f, -50f, 0f, 0f, o)
        assertTrue(o[2].isFinite() && o[0].isFinite())
    }

    @Test fun ringPointsStayOnTheCircle() {
        val p = FloatArray(3)
        for (k in 0 until 24) {
            HoloMath.ringPoint(2f, k * 0.31f, 1.2f, 0.35f, p)
            assertEquals(2f, sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]), 1e-4f)
        }
    }

    @Test fun hashIsStableAndInRange() {
        for (i in 0 until 200) { val h = HoloMath.hash(i); assertTrue(h in 0f..1f); assertEquals(h, HoloMath.hash(i), 0f) }
        assertNotEquals(HoloMath.hash(1), HoloMath.hash(2))
    }

    @Test fun easings() {
        assertEquals(0f, HoloMath.ease(0f), 1e-6f); assertEquals(1f, HoloMath.ease(1f), 1e-6f); assertEquals(1f, HoloMath.ease(3f), 1e-6f)
        assertEquals(0f, HoloMath.back(0f), 1e-5f); assertEquals(1f, HoloMath.back(1f), 1e-5f)
        assertTrue(HoloMath.back(0.8f) > 1f) // overshoots before settling
        assertEquals(0.25f, HoloMath.frac(3.25f), 1e-6f)
    }
}
