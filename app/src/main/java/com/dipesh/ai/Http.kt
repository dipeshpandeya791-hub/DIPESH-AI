package com.dipesh.ai

import com.dipesh.ai.net.ApiClient
import okhttp3.Request
import java.io.IOException

/** Small blocking HTTP helpers on the shared OkHttp client. Call from a background thread. */
object Http {
    fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "DipeshAI/2.0").build()
        ApiClient.okHttp.newCall(req).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            return it.body?.string().orEmpty()
        }
    }

    /** Returns (statusCode, responseBody). */
    fun postJson(url: String, headers: Map<String, String>, body: String): Pair<Int, String> {
        val builder = Request.Builder().url(url).post(ApiClient.jsonBody(body))
        headers.forEach { (k, v) -> builder.header(k, v) }
        ApiClient.okHttp.newCall(builder.build()).execute().use {
            return it.code to it.body?.string().orEmpty()
        }
    }
}
