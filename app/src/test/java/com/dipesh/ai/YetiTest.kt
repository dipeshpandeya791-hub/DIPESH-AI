package com.dipesh.ai

import com.dipesh.ai.pet.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class YetiTest {
    private fun brain() = YetiBrain(Random(7), sleepAfterMs = 10_000L)
    private fun run(b: YetiBrain, secs: Float, startMs: Long, input: PetInput, pose: Pose = Pose()): Long {
        var now = startMs; var t = 0f
        while (t < secs) { b.tick(0.05f, now, input, pose); t += 0.05f; now += 50 }
        return now
    }

    @Test fun assistantStatesDriveActs() {
        val b = brain(); val p = Pose()
        b.tick(0.05f, 1000, PetInput(status = "LISTENING"), p); assertEquals(Act.LISTEN, b.act)
        b.tick(0.05f, 1050, PetInput(status = "THINKING"), p); assertEquals(Act.THINK, b.act)
        b.tick(0.05f, 1100, PetInput(status = "SPEAKING", mouth = 0.6f), p); assertEquals(Act.SPEAK, b.act)
        assertEquals(0.6f, p.mouthOpen, 1e-4f)
    }

    @Test fun afterSpeechItIsHappyThenRelaxes() {
        val b = brain(); val p = Pose()
        b.tick(0.05f, 1000, PetInput(status = "SPEAKING"), p)
        b.tick(0.05f, 1050, PetInput(status = "IDLE"), p)
        assertEquals(Act.HAPPY, b.act)
    }

    @Test fun draggingShowsCarried() {
        val b = brain(); b.tick(0.05f, 1000, PetInput(dragging = true), Pose()); assertEquals(Act.CARRIED, b.act)
    }

    @Test fun sleepsWhenIdleAndWakesOnTouch() {
        val b = brain(); val p = Pose()
        val end = run(b, 30f, 1000, PetInput(), p)
        assertEquals(Act.SLEEP, b.act)
        assertEquals(0f, p.eyeOpen, 1e-3f)
        b.touched(end)
        assertEquals(Act.WAKE, b.act)
    }

    @Test fun doesNotSleepWhenDisabled() {
        val b = brain(); run(b, 40f, 1000, PetInput(sleepWhenIdle = false))
        assertNotEquals(Act.SLEEP, b.act)
    }

    @Test fun noWalkingWhenWanderOff() {
        val b = brain(); val p = Pose()
        var now = 1000L
        repeat(2000) { b.tick(0.05f, now, PetInput(wander = false, sleepWhenIdle = false), p); now += 50
            assertTrue(b.act != Act.WALK && b.act != Act.RUN); assertEquals(0f, b.velocityX, 0f) }
    }

    @Test fun reactsToEventsOnce() {
        val b = brain(); val p = Pose()
        b.tick(0.05f, 1000, PetInput(), p)
        b.react("error", 5L); assertEquals(Act.CONFUSED, b.act)
        b.react("celebrate", 5L); assertEquals(Act.CONFUSED, b.act) // same timestamp is ignored
        b.react("celebrate", 6L); assertEquals(Act.CELEBRATE, b.act)
    }

    @Test fun poseApproachIsSmoothAndConverges() {
        val a = Pose(armL = 0f); val t = Pose(armL = 100f)
        a.approach(t, 0.033f, 9f)
        assertTrue(a.armL > 0f && a.armL < 50f)
        repeat(200) { a.approach(t, 0.033f, 9f) }
        assertEquals(100f, a.armL, 0.5f)
    }

    @Test fun everyActProducesSanePose() {
        val b = brain(); val p = Pose()
        for (a in Act.values()) {
            for (t in listOf(0f, 0.3f, 1.1f, 2.5f)) {
                b.target(a, t, t * 3f, PetInput(micLevel = 0.5f), p)
                assertTrue("$a eyeOpen", p.eyeOpen in 0f..1.35f)
                assertTrue("$a mouth", p.mouthOpen in 0f..1f)
                assertTrue("$a arm", p.armL in -5f..200f && p.armR in -5f..200f)
                assertTrue("$a lift", p.lift in 0f..1f)
            }
        }
    }

    @Test fun frameRateIsLowerWhenResting() {
        assertTrue(YetiBrain.frameDelayMs(Act.SLEEP) > YetiBrain.frameDelayMs(Act.RUN))
    }

    @Test fun clampKeepsPetOnScreen() {
        assertEquals(0 to 90, PetPrefs.clamp(-50, 10, 100, 150, 1080, 2000, 90, 60))
        assertEquals(980 to 1790, PetPrefs.clamp(5000, 5000, 100, 150, 1080, 2000, 90, 60))
    }
}

class GeminiTtsTest {
    @Test fun requestBody() {
        val b = GeminiTts.buildBody("Hello", "Kore", "Excited")
        val text = b.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(text.endsWith(": Hello")); assertTrue(text.contains("excited", true))
        assertEquals("AUDIO", b.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0))
        assertEquals("Kore", b.getJSONObject("generationConfig").getJSONObject("speechConfig").getJSONObject("voiceConfig").getJSONObject("prebuiltVoiceConfig").getString("voiceName"))
    }
    @Test fun unknownVoiceFallsBack() {
        assertEquals("Aoede", GeminiTts.voice("nope").name)
    }
    @Test fun voiceCastIsComplete() {
        assertEquals(30, GeminiTts.voices.size); assertEquals(30, GeminiTts.voices.map { it.name }.toSet().size)
        assertTrue(GeminiTts.voices.all { it.group in GeminiTts.groups })
        assertTrue(GeminiTts.tones.containsKey("Natural"))
    }
    @Test fun parsesAudio() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val b64 = java.util.Base64.getEncoder().encodeToString(pcm)
        val json = """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/L16;rate=24000","data":"$b64"}}]}}]}"""
        assertArrayEquals(pcm, GeminiTts.parseAudio(json))
        assertNull(GeminiTts.parseAudio("""{"candidates":[]}"""))
        assertNull(GeminiTts.parseAudio("not json"))
    }
    @Test fun chunking() {
        assertTrue(GeminiTts.chunk("").isEmpty())
        assertEquals(listOf("Hi there. How are you?"), GeminiTts.chunk("Hi there. How are you?"))
        val long = (1..40).joinToString(" ") { "This is sentence number $it." }
        val parts = GeminiTts.chunk(long, 200)
        assertTrue(parts.size > 3); assertTrue(parts.all { it.length <= 200 })
        assertEquals(long.replace(" ", ""), parts.joinToString("").replace(" ", ""))
    }
    @Test fun loudness() {
        val silent = ByteArray(200); assertEquals(0f, GeminiTts.level(silent, 0, 200), 1e-6f)
        val loud = ByteArray(200) { if (it % 2 == 1) 100 else 0 }; assertTrue(GeminiTts.level(loud, 0, 200) > 0.5f)
    }
}
