package com.dipesh.ai

import com.dipesh.ai.net.ApiClient
import com.dipesh.ai.net.ApiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The AI brain: Gemini (streamed, default) or Claude, both with tool use so the assistant can act
 * on the phone. [onText] receives the full reply so far, which makes streaming UIs trivial.
 */
class Brain(private val store: Store, private val tools: Tools) {

    data class Result(val text: String, val isError: Boolean = false)

    suspend fun reply(history: List<ChatMsg>, image: String?, onText: (String) -> Unit): Result =
        withContext(Dispatchers.IO) {
            val openai = store.provider == "openai"
            val gemini = store.provider == Store.GEMINI
            val key = if (gemini) store.geminiKey else store.apiKey
            if (!openai && key.isBlank()) {
                val which = if (gemini) "Gemini" else "Anthropic"
                // Rule-based fallback: a few things work with no AI key at all.
                val offline = when (val intent = RuleBased.parse(history.lastOrNull { it.role == "user" }?.text.orEmpty())) {
                    RuleBased.Intent.Time -> RuleBased.timeText()
                    RuleBased.Intent.Date -> RuleBased.dateText()
                    RuleBased.Intent.Greeting -> "Hello! I'm DIPESH AI. Add an API key in Settings to unlock my full brain."
                    is RuleBased.Intent.OpenApp -> tools.run("open_app", JSONObject().put("name", intent.name))
                    is RuleBased.Intent.Flashlight -> tools.run("flashlight", JSONObject().put("on", intent.on))
                    RuleBased.Intent.ShowFeatures, RuleBased.Intent.HideFeatures -> null
                    null -> null
                }
                if (offline != null) { onText(offline); return@withContext Result(offline) }
                return@withContext Result(
                    "My AI brain is offline. Open Settings, then AI & API, and paste your $which API key. " +
                        "Until then I can still tell the time, open apps and control the flashlight.",
                    true
                )
            }
            try {
                if (openai) replyOpenAi(history, image, onText)
                else if (gemini) replyGemini(history, image, onText) else replyClaude(history, image, onText)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                Result(apiError(e.code, e.body, if (openai) "The AI server" else if (gemini) "Gemini" else "Claude"), true)
            } catch (e: IOException) {
                Result("I couldn't reach my brain. Check your internet connection and try again.", true)
            } catch (e: Exception) {
                Result("Something went wrong (${e.javaClass.simpleName}). Please try again.", true)
            }
        }

    // ---------------- Google Gemini (streaming) ----------------
    private suspend fun replyGemini(history: List<ChatMsg>, image: String?, onText: (String) -> Unit): Result {
        val contents = JSONArray()
        history.takeLast(20).dropWhile { it.role != "user" }.forEach {
            contents.put(
                JSONObject()
                    .put("role", if (it.role == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
            )
        }
        if (image != null && contents.length() > 0) {
            contents.getJSONObject(contents.length() - 1).getJSONArray("parts").put(
                JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", image))
            )
        }
        var model = store.geminiModel
        val system = systemPrompt()

        var withTools = true
        fun build(): String = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .apply {
                if (withTools) put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools.geminiDeclarations(recentUserText(history)))))
            }
            .put("generationConfig", JSONObject().put("maxOutputTokens", 2048))
            .toString()

        repeat(12) {
            val (stream, used) = try {
                openStream(build(), model)
            } catch (e: ApiError) {
                // A rejected tool list must not break chatting: retry once as a plain conversation.
                if (e.code == 400 && withTools) { withTools = false; openStream(build(), model) } else throw e
            }
            model = used
            onText("")

            val modelParts = JSONArray()
            val calls = ArrayList<JSONObject>()
            val shown = StringBuilder()
            stream.use { responseBody ->
                val source = responseBody.source()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val parts = JSONObject(data).optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        modelParts.put(p)
                        val fc = p.optJSONObject("functionCall")
                        if (fc != null) {
                            calls.add(fc)
                        } else if (!p.optBoolean("thought", false)) {
                            val t = p.optString("text")
                            if (t.isNotEmpty()) {
                                shown.append(t)
                                onText(shown.toString())
                            }
                        }
                    }
                }
            }

            if (calls.isEmpty()) {
                val text = shown.toString().trim()
                return if (text.isEmpty()) Result("Gemini returned an empty answer. Try rephrasing.", true) else Result(text)
            }

            // Replay the model turn exactly as received (keeps thought signatures intact), then answer the tool calls.
            contents.put(JSONObject().put("role", "model").put("parts", modelParts))
            val results = JSONArray()
            for (fc in calls) {
                val name = fc.getString("name")
                val out = tools.run(name, fc.optJSONObject("args") ?: JSONObject())
                val fr = JSONObject().put("name", name).put("response", JSONObject().put("result", out))
                val id = fc.optString("id")
                if (id.isNotEmpty()) fr.put("id", id)
                results.put(JSONObject().put("functionResponse", fr))
            }
            contents.put(JSONObject().put("role", "user").put("parts", results))
        }
        return Result("I got stuck in a loop. Please try again.", true)
    }

    /** Opens a streaming response, retrying when overloaded and falling back to other Gemini models. */
    private suspend fun openStream(body: String, preferred: String): Pair<ResponseBody, String> {
        val models = listOf(preferred, "gemini-3.6-flash", "gemini-2.5-flash").distinct()
        var lastCode = 0
        var lastBody = ""
        for (m in models) {
            for (attempt in 0 until 3) {
                val r = ApiClient.gemini.stream(m, "sse", store.geminiKey, ApiClient.jsonBody(body))
                if (r.isSuccessful) {
                    val b = r.body()
                    if (b != null) return b to m
                }
                lastCode = r.code()
                lastBody = r.errorBody()?.string().orEmpty()
                val busy = lastCode == 429 || lastCode == 500 || lastCode == 502 || lastCode == 503 || lastCode == 504
                if (!busy) break
                delay(1500L * (attempt + 1))
            }
            if (lastCode == 400 || lastCode == 401 || lastCode == 403) break
        }
        throw ApiError(lastCode, lastBody)
    }

    // ---------------- Anthropic Claude ----------------
    private suspend fun replyClaude(history: List<ChatMsg>, image: String?, onText: (String) -> Unit): Result {
        val msgs = JSONArray()
        history.takeLast(20).dropWhile { it.role != "user" }.forEach {
            msgs.put(JSONObject().put("role", it.role).put("content", it.text))
        }
        if (image != null && msgs.length() > 0) {
            val last = msgs.getJSONObject(msgs.length() - 1)
            val text = last.optString("content")
            last.put(
                "content", JSONArray()
                    .put(
                        JSONObject().put("type", "image").put(
                            "source", JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", image)
                        )
                    )
                    .put(JSONObject().put("type", "text").put("text", text))
            )
        }
        val system = systemPrompt()
        repeat(12) {
            val body = JSONObject()
                .put("model", store.model)
                .put("max_tokens", 900)
                .put("system", system)
                .put("tools", tools.schema(recentUserText(history)))
                .put("messages", msgs)
                .toString()
            val resp = ApiClient.claude.messages(store.apiKey, "2023-06-01", ApiClient.jsonBody(body))
            if (!resp.isSuccessful) throw ApiError(resp.code(), resp.errorBody()?.string().orEmpty())
            val json = JSONObject(resp.body()?.string().orEmpty())
            val content = json.getJSONArray("content")
            if (json.optString("stop_reason") != "tool_use") {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    if (b.optString("type") == "text") sb.append(b.optString("text"))
                }
                val text = sb.toString().trim()
                onText(text)
                return if (text.isEmpty()) Result("Claude returned an empty answer. Try rephrasing.", true) else Result(text)
            }
            msgs.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                if (b.optString("type") == "tool_use") {
                    val out = tools.run(b.getString("name"), b.optJSONObject("input") ?: JSONObject())
                    results.put(
                        JSONObject().put("type", "tool_result").put("tool_use_id", b.getString("id")).put("content", out)
                    )
                }
            }
            msgs.put(JSONObject().put("role", "user").put("content", results))
        }
        return Result("I got stuck in a loop. Please try again.", true)
    }

    // ---------------- Free OpenAI-compatible servers: Ollama, Groq, OpenRouter, LM Studio... ----------------
    private fun openAiTools(recent: String): JSONArray {
        val src = tools.schema(recent); val out = JSONArray()
        for (i in 0 until src.length()) {
            val t = src.getJSONObject(i); val sch = t.getJSONObject("input_schema")
            val params = JSONObject().put("type", "object").put("properties", sch.getJSONObject("properties"))
            if (sch.getJSONArray("required").length() > 0) params.put("required", sch.getJSONArray("required"))
            out.put(JSONObject().put("type", "function").put("function",
                JSONObject().put("name", t.getString("name")).put("description", t.getString("description")).put("parameters", params)))
        }
        return out
    }

    /** POSTs JSON to {base}/chat/completions over https (internet) or raw sockets (local/LAN server such as Ollama). */
    private fun openAiPost(body: String): Pair<Int, String> {
        val st = store.settings.value
        val base = st.openaiUrl.trim().trimEnd('/')
        val url = "$base/chat/completions"
        val headers = if (st.openaiKey.isNotBlank()) mapOf("Authorization" to "Bearer ${st.openaiKey.trim()}") else emptyMap()
        if (url.startsWith("https://")) return Http.postJson(url, headers, body)
        val u = android.net.Uri.parse(url)
        val r = LanHttp.request("POST", u.host ?: throw IOException("Bad server address"), if (u.port > 0) u.port else 80,
            u.encodedPath ?: "/", headers, body, 120_000)
        return r.code to r.text
    }

    private fun replyOpenAi(history: List<ChatMsg>, image: String?, onText: (String) -> Unit): Result {
        val st = store.settings.value
        if (st.openaiUrl.isBlank()) return Result("Set the server address in Settings > AI & API > Free / local server.", true)
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt()))
        history.takeLast(20).dropWhile { it.role != "user" }.forEach {
            msgs.put(JSONObject().put("role", if (it.role == "user") "user" else "assistant").put("content", it.text))
        }
        if (image != null && msgs.length() > 1) {
            val last = msgs.getJSONObject(msgs.length() - 1)
            last.put("content", JSONArray()
                .put(JSONObject().put("type", "text").put("text", last.getString("content")))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image"))))
        }
        val toolList = openAiTools(recentUserText(history))
        var useTools = true
        repeat(12) {
            val req = JSONObject().put("model", st.openaiModel.ifBlank { "llama3.2" }).put("messages", msgs).put("stream", false)
            if (useTools) req.put("tools", toolList)
            val (code, text) = try { openAiPost(req.toString()) } catch (e: java.net.ConnectException) {
                return Result("I couldn't connect to the AI server at ${st.openaiUrl}. Start it (for Ollama: run 'ollama serve' in Termux or on your PC) and check the address.", true)
            }
            if (code == 400 && useTools && (text.contains("tool", true) || text.contains("function", true))) {
                useTools = false // this model can't use tools: continue as a plain chat instead of failing
                return@repeat
            }
            if (code !in 200..299) throw ApiError(code, text)
            val message = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val calls = message.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) {
                val answer = message.optString("content", "").trim()
                onText(answer)
                return if (answer.isEmpty()) Result("The AI server returned an empty answer. Try rephrasing.", true) else Result(answer)
            }
            msgs.put(message)
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i); val f = c.getJSONObject("function")
                val args = try { JSONObject(f.optString("arguments").ifBlank { "{}" }) } catch (_: Exception) { JSONObject() }
                val out = tools.run(f.getString("name"), args)
                msgs.put(JSONObject().put("role", "tool").put("tool_call_id", c.optString("id")).put("content", out))
            }
        }
        return Result("I got stuck in a loop. Please try again.", true)
    }

    private fun recentUserText(history: List<ChatMsg>): String =
        history.filter { it.role == "user" }.takeLast(3).joinToString(" ") { it.text }

    private fun apiError(code: Int, body: String, who: String): String {
        val msg = try {
            JSONObject(body).getJSONObject("error").getString("message")
        } catch (e: Exception) {
            body.take(120)
        }
        val keyProblem = code == 401 || code == 403 || msg.contains("API key not valid", true)
        return when {
            keyProblem -> "The $who API key was rejected. Please check it in Settings."
            code == 429 -> "$who says I'm asking too fast or the free quota is used up. Try again in a minute."
            code == 503 -> "$who is very busy right now. I tried other models too. Please try again in a moment."
            code == 404 -> "$who doesn't know that model name. Check the model in Settings."
            else -> "$who returned error $code: ${msg.take(160)}"
        }
    }

    private fun systemPrompt(): String {
        val s = store.settings.value
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.US).format(Date())
        val facts = store.facts()
        val persona = when (s.personality) {
            "friendly" -> "You are warm, upbeat and encouraging, like a close friend who is great at getting things done."
            "professional" -> "You are formal, precise and courteous, like a top executive assistant."
            "concise" -> "You are extremely brief. Answer in one short sentence whenever possible."
            "custom" -> "Follow the user's custom personality instructions below."
            else -> "You are capable, warm and a little witty, in the style of JARVIS."
        }
        return buildString {
            append("You are DIPESH AI, a personal voice assistant living on the user's Android phone. ")
            append(persona).append(' ')
            if (s.userName.isNotBlank()) append("The user's name is ${s.userName}. ")
            append("Replies are often spoken aloud, so prefer 1-4 short sentences; use markdown lists or code blocks only when they really help. ")
            append("Use your tools to act on the phone and fetch live information instead of guessing. ")
            append("Reply in the language the user speaks, whichever it is (you know every major language). Preferred language: ${Locale.forLanguageTag(s.language).getDisplayName(Locale.ENGLISH)} (${s.language}). ")
            append("Current date and time: $now. ")
            if (s.city.isNotBlank()) append("The user's city: ${s.city}. ")
            if (facts.isNotEmpty()) append("Things you remember about the user: ").append(facts.joinToString("; ")).append(". ")
            if (s.customInstructions.isNotBlank()) append("Extra instructions from the user: ${s.customInstructions} ")
            append("You can see photos the user takes with the camera and screenshots they share. You can search the web with search_web, manage files, send WhatsApp messages and run saved automation routines with run_routine. ")
            append("To operate other apps, call read_screen to see what is displayed, then tap_text, type_text, scroll_screen or press_button, and check again with read_screen. ")
            append("Calls and text messages need the user's approval, which the app asks for automatically. ")
            append("When the user tells you something worth remembering long-term, call remember_fact. ")
            append("You are also an autonomous coding and systems agent, all on free tools: run shell commands in Termux with termux_run (python, node, git), test logic with run_javascript, keep projects in the workspace with read_text_file/write_text_file, and work on GitHub with the github_* tools (pull a repo, edit, commit, open pull requests, check CI with github_build_status and read failures with github_failed_log). Install extra skills from GitHub with install_skill. ")
            append("Work like an engineer: plan briefly, act, check the result, and when a tool returns an error, diagnose the cause from the message, fix it and retry with a different approach (at most 3 attempts, never repeat an identical failing call); call self_check when something keeps failing. Say plainly what you could not do and why, with the exact fix for the user. ")
            append("Control smart devices on the home network (TVs, plugs, lights, cameras, computers) with device_list/device_add/device_control and wake_on_lan; build Excel files with create_spreadsheet; type messages or data into the app on screen with type_lines. ")
            append("Everything you use must be free: never suggest paid services. ")
            Skills.promptBlock(store).takeIf { it.isNotBlank() }?.let { append(it) }
        }
    }
}
