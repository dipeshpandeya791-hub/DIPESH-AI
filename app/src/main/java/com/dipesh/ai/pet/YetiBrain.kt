package com.dipesh.ai.pet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything the Yeti can be doing. */
enum class Act {
    IDLE, SIT, WALK, RUN, JUMP, DANCE, WAVE, CLIMB, SLEEP, SLEEPY, WAKE,
    LISTEN, THINK, SPEAK, HAPPY, EXCITED, CURIOUS, SURPRISED, SAD, CONFUSED, LAUGH, CELEBRATE, CARRIED
}

/** A full body pose. Every field is a smooth value so the renderer can blend between any two poses. */
class Pose(
    var bob: Float = 0f,        // body up/down, in "units" (1 = about 4% of height)
    var squash: Float = 0f,     // -1..1, positive = squashed/wide
    var lean: Float = 0f,       // body lean in degrees
    var headTilt: Float = 0f,   // degrees
    var lookX: Float = 0f,      // -1..1 head/eyes direction
    var lookY: Float = 0f,
    var armL: Float = 20f,      // degrees; 0 = hanging, 180 = straight up
    var armR: Float = 20f,
    var legL: Float = 0f,       // leg swing -1..1
    var legR: Float = 0f,
    var lift: Float = 0f,       // jump height, 0..1
    var eyeOpen: Float = 1f,    // 0 closed .. 1 open (1.2 = wide)
    var mouthOpen: Float = 0f,  // 0..1
    var smile: Float = 0.4f,    // -1 sad .. 1 big smile
    var brow: Float = 0f,       // -1 worried/angry .. 1 raised
    var ear: Float = 0f,        // ear wiggle -1..1
    var blush: Float = 0.3f,
    var sparkle: Float = 0f     // eye sparkle / glow strength
) {
    fun copyFrom(o: Pose) {
        bob = o.bob; squash = o.squash; lean = o.lean; headTilt = o.headTilt; lookX = o.lookX; lookY = o.lookY
        armL = o.armL; armR = o.armR; legL = o.legL; legR = o.legR; lift = o.lift; eyeOpen = o.eyeOpen
        mouthOpen = o.mouthOpen; smile = o.smile; brow = o.brow; ear = o.ear; blush = o.blush; sparkle = o.sparkle
    }

    /** Moves this pose toward [t] with frame-rate independent smoothing (k ~ 6 soft, 14 snappy). */
    fun approach(t: Pose, dt: Float, k: Float) {
        val a = 1f - Math.exp((-k * dt).toDouble()).toFloat()
        fun m(x: Float, y: Float) = x + (y - x) * a
        bob = m(bob, t.bob); squash = m(squash, t.squash); lean = m(lean, t.lean); headTilt = m(headTilt, t.headTilt)
        lookX = m(lookX, t.lookX); lookY = m(lookY, t.lookY); armL = m(armL, t.armL); armR = m(armR, t.armR)
        legL = m(legL, t.legL); legR = m(legR, t.legR); lift = m(lift, t.lift); eyeOpen = m(eyeOpen, t.eyeOpen)
        mouthOpen = m(mouthOpen, t.mouthOpen); smile = m(smile, t.smile); brow = m(brow, t.brow); ear = m(ear, t.ear)
        blush = m(blush, t.blush); sparkle = m(sparkle, t.sparkle)
    }
}

/** What the outside world tells the pet each frame. */
data class PetInput(
    val status: String = "IDLE",      // assistant state: IDLE, LISTENING, THINKING, SPEAKING
    val micLevel: Float = 0f,         // 0..1 while listening
    val mouth: Float = 0f,            // 0..1 loudness of speech, 0 when unknown
    val dragging: Boolean = false,
    val wander: Boolean = true,
    val sleepWhenIdle: Boolean = true
)

/**
 * The Yeti's mind: picks what to do, for how long, and what pose that means. Pure logic with an injectable clock
 * and random source so it can be unit-tested. Rendering and window movement live elsewhere.
 */
class YetiBrain(private val rnd: java.util.Random = java.util.Random(), private val sleepAfterMs: Long = 75_000L) {
    var act: Act = Act.IDLE; private set
    var actTime = 0f; private set          // seconds in the current act
    private var actLength = 4f             // seconds this act should last (0 = until changed)
    var facing = 1; private set            // 1 right, -1 left
    private var lastActivityMs = 0L
    private var lastEventAt = 0L
    private var nextBlink = 2f
    private var blinkT = -1f
    private var clock = 0f
    private var lookTarget = 0f
    private var nextLook = 1.5f
    private var wasStatus = "IDLE"
    var velocityX = 0f; private set        // dp/sec the window should move

    /** The user touched the pet. */
    fun touched(nowMs: Long) {
        lastActivityMs = nowMs
        if (act == Act.SLEEP || act == Act.SLEEPY) start(Act.WAKE, 1.6f) else if (act == Act.IDLE || act == Act.SIT) start(Act.HAPPY, 1.5f)
    }

    fun petted(nowMs: Long) { lastActivityMs = nowMs; start(Act.LAUGH, 2f) }

    /** Called when the screen is at an edge while walking. */
    fun hitEdge() {
        if (act == Act.WALK || act == Act.RUN) {
            if (rnd.nextInt(3) == 0) start(Act.CLIMB, 2.4f) else { facing = -facing; }
        }
    }

    private fun start(a: Act, length: Float) {
        act = a; actTime = 0f; actLength = length
        if (a == Act.WALK || a == Act.RUN) velocityX = (if (a == Act.RUN) 120f else 48f) * facing else velocityX = 0f
    }

    /** Advance the mind by [dt] seconds. Returns the pose the body should be moving toward. */
    fun tick(dt: Float, nowMs: Long, input: PetInput, out: Pose): Act {
        clock += dt; actTime += dt
        if (lastActivityMs == 0L) lastActivityMs = nowMs
        var busy = true
        // ---- things the assistant is doing take priority over everything ----
        val forced: Act? = when {
            input.dragging -> Act.CARRIED
            input.status == "LISTENING" -> Act.LISTEN
            input.status == "THINKING" -> Act.THINK
            input.status == "SPEAKING" -> Act.SPEAK
            else -> null
        }
        if (forced != null) {
            if (act != forced) start(forced, 0f)
            lastActivityMs = nowMs
        } else if (act == Act.LISTEN || act == Act.THINK || act == Act.SPEAK || act == Act.CARRIED) {
            start(Act.IDLE, 2f) // assistant finished: relax
            if (wasStatus == "SPEAKING") start(Act.HAPPY, 1.2f)
        } else {
            busy = false
        }
        wasStatus = input.status

        if (!busy) {
            if (act == Act.SLEEPY && actTime >= actLength) start(Act.SLEEP, 0f)
            else if (actLength > 0f && actTime >= actLength) next(nowMs, input)
            if (!input.wander && (act == Act.WALK || act == Act.RUN || act == Act.CLIMB)) start(Act.IDLE, 3f)
            // inactivity -> sleepy -> sleep
            if (input.sleepWhenIdle && act != Act.SLEEP && act != Act.SLEEPY && nowMs - lastActivityMs > sleepAfterMs) start(Act.SLEEPY, 5f)
        }

        // blinking and glancing run all the time
        nextBlink -= dt
        if (nextBlink <= 0f && blinkT < 0f) { blinkT = 0f; nextBlink = 2f + rnd.nextFloat() * 4f }
        if (blinkT >= 0f) { blinkT += dt; if (blinkT > 0.18f) blinkT = -1f }
        nextLook -= dt
        if (nextLook <= 0f) { lookTarget = (rnd.nextFloat() * 2f - 1f) * 0.8f; nextLook = 1.2f + rnd.nextFloat() * 3f }

        target(act, actTime, clock, input, out)
        // blink
        if (blinkT >= 0f && act != Act.SLEEP) out.eyeOpen = min(out.eyeOpen, abs(blinkT - 0.09f) / 0.09f)
        return act
    }

    /** One-shot reactions posted by the app (success, error, task celebration). */
    fun react(event: String, at: Long) {
        if (at == lastEventAt) return
        lastEventAt = at
        when (event) {
            "success" -> start(Act.HAPPY, 1.8f)
            "celebrate" -> start(Act.CELEBRATE, 3.2f)
            "error" -> start(Act.CONFUSED, 2.4f)
        }
    }

    private fun next(nowMs: Long, input: PetInput) {
        // weighted random choice of the next idle behaviour; calm things are more likely than energetic ones
        val r = rnd.nextInt(100)
        when {
            r < 28 -> start(Act.IDLE, 3f + rnd.nextFloat() * 4f)
            r < 40 -> start(Act.SIT, 5f + rnd.nextFloat() * 5f)
            r < 54 -> if (input.wander) { if (rnd.nextBoolean()) facing = -facing; start(Act.WALK, 2.5f + rnd.nextFloat() * 3f) } else start(Act.IDLE, 3f)
            r < 60 -> if (input.wander) { if (rnd.nextBoolean()) facing = -facing; start(Act.RUN, 1.5f + rnd.nextFloat() * 1.5f) } else start(Act.IDLE, 3f)
            r < 70 -> start(Act.WAVE, 2.2f)
            r < 78 -> start(Act.CURIOUS, 2.6f)
            r < 85 -> start(Act.JUMP, 1.1f)
            r < 91 -> start(Act.DANCE, 4f)
            r < 95 -> start(Act.SURPRISED, 1.4f)
            r < 98 -> start(Act.SAD, 2.2f)
            else -> start(Act.CONFUSED, 2f)
        }
    }

    private fun wave(t: Float, speed: Float) = sin(t * speed * 2f * PI.toFloat())

    /** The pose each act is moving toward at time [t] (seconds into the act); [c] is the global clock. */
    fun target(a: Act, t: Float, c: Float, input: PetInput, o: Pose) {
        // neutral base with gentle breathing
        val breath = sin(c * 2.1f)
        o.bob = breath * 0.35f; o.squash = breath * 0.05f; o.lean = 0f; o.headTilt = 0f
        o.lookX = lookTarget; o.lookY = 0f; o.armL = 22f + breath * 2f; o.armR = 22f + breath * 2f
        o.legL = 0f; o.legR = 0f; o.lift = 0f; o.eyeOpen = 1f; o.mouthOpen = 0f; o.smile = 0.45f
        o.brow = 0f; o.ear = sin(c * 0.9f) * 0.15f; o.blush = 0.3f; o.sparkle = 0.2f
        when (a) {
            Act.IDLE -> {}
            Act.SIT -> { o.bob = -1.6f + breath * 0.25f; o.squash = 0.18f; o.legL = 1.2f; o.legR = -1.2f; o.armL = 35f; o.armR = 35f; o.smile = 0.5f }
            Act.WALK -> { val w = wave(t, 1.6f); o.bob = abs(w) * 0.9f; o.lean = 4f * facing; o.legL = w; o.legR = -w; o.armL = 20f + w * 28f; o.armR = 20f - w * 28f; o.lookX = 0.6f * facing; o.smile = 0.6f }
            Act.RUN -> { val w = wave(t, 3.1f); o.bob = abs(w) * 1.6f; o.lean = 11f * facing; o.legL = w * 1.4f; o.legR = -w * 1.4f; o.armL = 40f + w * 45f; o.armR = 40f - w * 45f; o.lookX = 0.8f * facing; o.smile = 0.8f; o.mouthOpen = 0.35f; o.ear = 0.7f * -facing }
            Act.JUMP -> { val p = (t / 1.1f).coerceIn(0f, 1f); o.lift = sin(p * PI.toFloat()); o.squash = if (p < 0.18f || p > 0.85f) 0.3f else -0.18f; o.armL = 150f; o.armR = 150f; o.legL = -0.5f; o.legR = 0.5f; o.smile = 0.9f; o.mouthOpen = 0.4f; o.eyeOpen = 1.15f; o.sparkle = 0.6f }
            Act.DANCE -> { val w = wave(t, 1.8f); val w2 = wave(t, 3.6f); o.bob = abs(w2) * 1.0f; o.lean = w * 8f; o.headTilt = w * 10f; o.armL = 100f + w * 60f; o.armR = 100f - w * 60f; o.legL = w2; o.legR = -w2; o.smile = 0.95f; o.mouthOpen = 0.3f + 0.2f * abs(w); o.sparkle = 0.7f; o.ear = w; o.blush = 0.5f }
            Act.WAVE -> { val w = wave(t, 2.6f); o.armR = 150f + w * 22f; o.smile = 0.8f; o.headTilt = 4f; o.lookX = 0f; o.blush = 0.45f; o.mouthOpen = 0.15f }
            Act.CLIMB -> { val w = wave(t, 2.2f); o.lean = 70f * facing; o.armL = 160f + w * 15f; o.armR = 160f - w * 15f; o.legL = w * 0.8f; o.legR = -w * 0.8f; o.bob = abs(w) * 0.5f; o.lookY = -0.6f; o.eyeOpen = 1.1f; o.smile = 0.3f }
            Act.SLEEP -> { o.bob = -1.4f + breath * 0.5f; o.squash = 0.22f + breath * 0.04f; o.eyeOpen = 0f; o.headTilt = 12f; o.lean = 6f; o.legL = 1.2f; o.legR = -1.2f; o.armL = 40f; o.armR = 40f; o.smile = 0.2f; o.blush = 0.35f; o.sparkle = 0f; o.lookX = 0f; o.ear = -0.4f }
            Act.SLEEPY -> { o.eyeOpen = 0.45f + 0.2f * sin(c * 0.8f); o.headTilt = 8f * sin(c * 0.5f); o.bob = -0.5f; o.mouthOpen = 0.3f * max(0f, sin(c * 0.7f)); o.armL = 15f; o.armR = 15f; o.smile = 0.1f; o.lookY = 0.4f }
            Act.WAKE -> { val p = (t / 1.6f).coerceIn(0f, 1f); o.eyeOpen = if (p < 0.3f) p / 0.3f * 0.6f else 1f; o.armL = 130f * (1f - p) + 20f; o.armR = 130f * (1f - p) + 20f; o.mouthOpen = 0.6f * (1f - p); o.squash = -0.1f * (1f - p); o.bob = 0.6f; o.smile = 0.3f + 0.4f * p }
            Act.LISTEN -> { o.headTilt = 9f + sin(c * 1.3f) * 3f; o.eyeOpen = 1.2f; o.ear = 0.5f + input.micLevel * 1.2f; o.lookX = 0f; o.armL = 28f; o.armR = 28f; o.sparkle = 0.5f + input.micLevel; o.smile = 0.5f; o.brow = 0.5f; o.bob = 0.4f + input.micLevel * 1.2f }
            Act.THINK -> { o.headTilt = -10f; o.lookX = -0.5f + 0.3f * sin(c * 1.1f); o.lookY = -0.7f; o.armR = 95f; o.armL = 20f; o.brow = 0.6f; o.smile = 0.1f; o.ear = sin(c * 2f) * 0.3f; o.sparkle = 0.4f + 0.2f * sin(c * 3f); o.eyeOpen = 0.95f }
            Act.SPEAK -> { val m = if (input.mouth > 0.02f) input.mouth else (0.25f + 0.55f * abs(sin(c * 9f)) * (0.6f + 0.4f * sin(c * 2.3f))); o.mouthOpen = m.coerceIn(0f, 1f); o.smile = 0.5f; o.armL = 30f + 18f * sin(c * 2.2f); o.armR = 30f - 18f * sin(c * 2.2f); o.headTilt = 3f * sin(c * 1.4f); o.bob = m * 0.6f; o.blush = 0.4f }
            Act.HAPPY -> { o.smile = 1f; o.mouthOpen = 0.35f; o.armL = 80f + wave(t, 2f) * 15f; o.armR = 80f - wave(t, 2f) * 15f; o.bob = abs(wave(t, 2.5f)) * 1.2f; o.blush = 0.55f; o.sparkle = 0.7f; o.eyeOpen = 0.8f }
            Act.EXCITED -> { val w = wave(t, 4f); o.smile = 1f; o.mouthOpen = 0.5f; o.armL = 140f + w * 20f; o.armR = 140f - w * 20f; o.bob = abs(w) * 1.5f; o.eyeOpen = 1.2f; o.sparkle = 1f; o.ear = w }
            Act.CURIOUS -> { o.headTilt = 14f; o.lookX = 0.6f * sin(t * 1.5f); o.brow = 0.8f; o.eyeOpen = 1.2f; o.ear = 0.6f; o.armL = 22f; o.armR = 60f; o.smile = 0.3f; o.lean = 3f * sin(t) }
            Act.SURPRISED -> { val p = (t / 0.25f).coerceIn(0f, 1f); o.eyeOpen = 1.3f; o.mouthOpen = 0.8f * p; o.brow = 1f; o.armL = 120f * p; o.armR = 120f * p; o.lift = 0.15f * (1f - p); o.squash = -0.1f; o.smile = 0f; o.ear = 0.9f; o.bob = 0.6f }
            Act.SAD -> { o.smile = -0.8f; o.brow = -0.8f; o.headTilt = -6f; o.lookY = 0.7f; o.bob = -0.6f; o.armL = 8f; o.armR = 8f; o.eyeOpen = 0.8f; o.ear = -0.8f; o.sparkle = 0.8f; o.blush = 0.1f }
            Act.CONFUSED -> { o.headTilt = 16f * sin(t * 2.2f); o.brow = -0.5f; o.lookX = sin(t * 3f); o.armL = 60f; o.armR = 100f; o.smile = -0.1f; o.mouthOpen = 0.18f; o.ear = sin(t * 4f) * 0.6f; o.eyeOpen = 1.05f }
            Act.LAUGH -> { val w = wave(t, 5f); o.smile = 1f; o.mouthOpen = 0.55f + 0.3f * w; o.eyeOpen = 0.3f; o.bob = abs(w) * 1.4f; o.squash = 0.1f * w; o.armL = 70f; o.armR = 70f; o.lean = w * 5f; o.blush = 0.7f; o.sparkle = 0.5f }
            Act.CELEBRATE -> { val w = wave(t, 2.4f); o.lift = abs(w) * 0.8f; o.armL = 160f; o.armR = 160f; o.smile = 1f; o.mouthOpen = 0.6f; o.eyeOpen = 1.1f; o.sparkle = 1f; o.blush = 0.6f; o.legL = w * 0.6f; o.legR = -w * 0.6f; o.ear = w }
            Act.CARRIED -> { o.lift = 0.1f; o.legL = 0.6f * sin(c * 6f); o.legR = -0.6f * sin(c * 6f); o.armL = 150f; o.armR = 150f; o.eyeOpen = 1.2f; o.mouthOpen = 0.35f; o.smile = 0.3f; o.squash = -0.12f; o.brow = 0.6f; o.ear = 0.6f }
        }
    }

    companion object {
        /** Frame interval (ms) that is smooth when it matters and cheap when the pet is nearly still. */
        fun frameDelayMs(a: Act): Long = when (a) {
            Act.SLEEP -> 110L
            Act.SIT, Act.IDLE, Act.SLEEPY, Act.CURIOUS -> 50L
            else -> 33L
        }
    }
}
