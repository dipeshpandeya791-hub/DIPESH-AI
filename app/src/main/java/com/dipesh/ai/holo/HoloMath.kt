package com.dipesh.ai.holo

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Small, allocation-free 3D helpers behind the holographic stage: rotate, project with perspective, ring points. Unit-tested. */
object HoloMath {
    /** Camera distance in world units; the core has radius 1. */
    const val CAM = 5f

    /**
     * Rotates a world point by [yaw] (around Y) then [pitch] (around X) and projects it with perspective.
     * out = [screenX, screenY (world units, y down), scale, depth]; depth > 0 means away from the viewer.
     */
    fun project(x: Float, y: Float, z: Float, yaw: Float, pitch: Float, out: FloatArray) {
        val cy = cos(yaw); val sy = sin(yaw)
        val x1 = x * cy + z * sy
        val z1 = -x * sy + z * cy
        val cp = cos(pitch); val sp = sin(pitch)
        val y2 = y * cp - z1 * sp
        val z2 = y * sp + z1 * cp
        val k = CAM / (CAM + z2).coerceAtLeast(0.25f)
        out[0] = x1 * k; out[1] = y2 * k; out[2] = k; out[3] = z2
    }

    /** A point on a circle of radius [r] (angle [theta]) in the XZ plane, tilted about X by [tiltX] and about Z by [tiltZ]. */
    fun ringPoint(r: Float, theta: Float, tiltX: Float, tiltZ: Float, out: FloatArray) {
        val x = r * cos(theta); val z = r * sin(theta)
        val y1 = -z * sin(tiltX); val z1 = z * cos(tiltX)
        out[0] = x * cos(tiltZ) - y1 * sin(tiltZ)
        out[1] = x * sin(tiltZ) + y1 * cos(tiltZ)
        out[2] = z1
    }

    /** Deterministic pseudo-random 0..1 from an integer seed (stable particle layout without storing any state). */
    fun hash(seed: Int): Float {
        val v = sin(seed * 12.9898f) * 43758.547f
        return v - floor(v)
    }

    fun ease(t: Float): Float { val c = t.coerceIn(0f, 1f); return 1f - (1f - c) * (1f - c) * (1f - c) }

    /** Overshooting ease used when the rings snap open during the startup sequence. */
    fun back(t: Float): Float {
        val c = t.coerceIn(0f, 1f); val s = 1.70158f
        val u = c - 1f
        return u * u * ((s + 1f) * u + s) + 1f
    }

    fun frac(v: Float): Float = v - floor(v)
}
