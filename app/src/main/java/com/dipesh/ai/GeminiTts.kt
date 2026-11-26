package com.dipesh.ai

import com.dipesh.ai.net.ApiClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** Natural, expressive speech from Gemini's text-to-speech models (uses the same Gemini key as the brain). */
object GeminiTts {
    data class Voice(val name: String, val trait: String, val group: String)

    /** Gemini's prebuilt voices. Groups are only for filtering in Settings. */
    val voices = listOf(
        Voice("Aoede", "Breezy & Natural", "Bright"), Voice("Sulafat", "Warm & Velvet", "Warm"),
        Voice("Kore", "Balanced & Warm", "Warm"), Voice("Vindemiatrix", "Gentle & Soothing", "Warm"),
        Voice("Achird", "Friendly", "Warm"), Voice("Zephyr", "Bright", "Bright"),
        Voice("Puck", "Upbeat", "Bright"), Voice("Leda", "Youthful", "Bright"),
        Voice("Autonoe", "Bright", "Bright"), Voice("Laomedeia", "Upbeat", "Bright"),
        Voice("Sadachbia", "Lively", "Bright"), Voice("Fenrir", "Excitable", "Bright"),
        Voice("Callirrhoe", "Easy-going", "Warm"), Voice("Umbriel", "Easy-going", "Warm"),
        Voice("Achernar", "Soft", "Warm"), Voice("Algieba", "Smooth", "Warm"),
        Voice("Despina", "Smooth", "Warm"), Voice("Enceladus", "Breathy", "Warm"),
        Voice("Zubenelgenubi", "Casual", "Warm"), Voice("Charon", "Informative", "Deep"),
        Voice("Orus", "Firm", "Deep"), Voice("Alnilam", "Firm", "Deep"),
        Voice("Algenib", "Gravelly", "Deep"), Voice("Rasalgethi", "Informative", "Deep"),
        Voice("Gacrux", "Mature", "Deep"), Voice("Iapetus", "Clear", "Deep"),
        Voice("Erinome", "Clear", "Deep"), Voice("Schedar", "Even", "Deep"),
        Voice("Pulcherrima", "Forward", "Deep"), Voice("Sadaltager", "Knowledgeable", "Deep")
    )

    val groups = listOf("All", "Warm", "Bright", "Deep")

    /** Delivery styles; the text is the instruction put in front of what is spoken. */
    val tones = linkedMapOf(
        "Natural" to "Say this in a natural, relaxed, friendly conversational way, like a real person talking",
        "Neutral" to "Say this in a clear, neutral tone",
        "Joyful" to "Say this in a joyful, smiling voice",
        "Excited" to "Say this in an excited, energetic way",
        "Calm" to "Say this in a calm, slow, reassuring voice",
        "Empathetic" to "Say this in a warm, empathetic, caring voice",
        "Dramatic" to "Say this in a dramatic, expressive way",
        "Suspenseful" to "Say this in a hushed, suspenseful way",
        "Storyteller" to "Say this like a storyteller reading to a friend"
    )

    const val DEFAULT_MODEL = "gemini-2.5-flash-preview-tts"
    const val SAMPLE_RATE = 24_000

    fun voice(name: String) = voices.firstOrNull { it.name == name } ?: voices.first()

    fun buildBody(text: String, voice: String, tone: String): JSONObject {
        val style = tones[tone] ?: tones.getValue("Natural")
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "$style: $text")))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig",
                    JSONObject().put("voiceName", voice(voice).name)))))
    }

    /** Raw 16-bit mono PCM (24 kHz) from a generateContent response, or null when there is none. */
    fun parseAudio(json: String): ByteArray? = try {
        val parts = JSONObject(json).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        var data: String? = null
        for (i in 0 until parts.length()) {
            val inline = parts.getJSONObject(i).optJSONObject("inlineData") ?: parts.getJSONObject(i).optJSONObject("inline_data")
            if (inline != null) { data = inline.optString("data"); break }
        }
        if (data.isNullOrBlank()) null else Base64.getMimeDecoder().decode(data)
    } catch (_: Exception) { null }

    /** Splits a reply into sentence-sized chunks so playback can start after the first one. */
    fun chunk(text: String, max: Int = 280): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        val cur = StringBuilder()
        Regex("(?<=[.!?।।\\n])\\s+").split(clean).forEach { sentence ->
            var s = sentence.trim()
            while (s.length > max) {
                val cut = s.lastIndexOf(' ', max).takeIf { it > 40 } ?: max
                if (cur.isNotEmpty()) { out.add(cur.toString()); cur.clear() }
                out.add(s.substring(0, cut).trim()); s = s.substring(cut).trim()
            }
            if (s.isEmpty()) return@forEach
            if (cur.length + s.length + 1 > max && cur.isNotEmpty()) { out.add(cur.toString()); cur.clear() }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    /** Peak-ish loudness 0..1 of a PCM16 buffer; drives the pet's mouth. */
    fun level(pcm: ByteArray, from: Int, len: Int): Float {
        var sum = 0.0; var n = 0; var i = from
        while (i + 1 < from + len && i + 1 < pcm.size) {
            val v = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            sum += v.toDouble() * v; n++; i += 2
        }
        return if (n == 0) 0f else (Math.sqrt(sum / n) / 6000.0).toFloat().coerceIn(0f, 1f)
    }

    class Failure(val code: Int, message: String) : Exception(message)

    /** Network call. Throws [Failure] (429 = free-tier rate limit) or IOException. */
    fun synthesize(key: String, model: String, text: String, voice: String, tone: String): ByteArray {
        val body = buildBody(text, voice, tone).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${model.ifBlank { DEFAULT_MODEL }}:generateContent")
            .header("x-goog-api-key", key).post(body).build()
        ApiClient.okHttp.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw Failure(r.code, s.take(200))
            return parseAudio(s) ?: throw Failure(-1, "no audio in response")
        }
    }
}
