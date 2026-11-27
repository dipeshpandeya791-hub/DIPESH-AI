package com.dipesh.ai.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import java.util.concurrent.TimeUnit

/** Google Gemini REST API. Bodies are built as JSON by [com.dipesh.ai.Brain]. */
interface GeminiApi {
    @Streaming
    @POST("v1beta/models/{model}:streamGenerateContent")
    suspend fun stream(
        @Path("model") model: String,
        @Query("alt") alt: String,
        @Header("x-goog-api-key") key: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
}

/** Anthropic Claude REST API. */
interface ClaudeApi {
    @POST("v1/messages")
    suspend fun messages(
        @Header("x-api-key") key: String,
        @Header("anthropic-version") version: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
}

class ApiError(val code: Int, val body: String) : Exception("API error $code")

object ApiClient {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val gemini: GeminiApi by lazy {
        Retrofit.Builder().baseUrl("https://generativelanguage.googleapis.com/").client(okHttp).build()
            .create(GeminiApi::class.java)
    }

    val claude: ClaudeApi by lazy {
        Retrofit.Builder().baseUrl("https://api.anthropic.com/").client(okHttp).build()
            .create(ClaudeApi::class.java)
    }

    /** Opens the TLS connection to Gemini ahead of the first question so the reply starts sooner. */
    fun prewarm() {
        try {
            val req = okhttp3.Request.Builder().url("https://generativelanguage.googleapis.com/").head().build()
            okHttp.newCall(req).execute().close()
        } catch (_: Exception) { /* offline is fine; the real call will report it */ }
    }

    fun jsonBody(json: String): RequestBody = json.toRequestBody(jsonType)
}
