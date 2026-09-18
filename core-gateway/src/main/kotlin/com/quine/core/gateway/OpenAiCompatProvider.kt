package com.quine.core.gateway

import com.quine.core.common.LogRedaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容适配器（主轴）：覆盖 DeepSeek / Kimi / GLM / Qwen 以及任何自建兼容服务。
 * 负责 SSE 解析、增量工具调用拼装、usage 统计、错误映射与重试预算。
 */
class OpenAiCompatProvider(
    private val baseUrl: String,
    private val model: String,
    private val apiKeyProvider: suspend () -> String?,
    private val client: OkHttpClient = defaultClient(),
    private val json: Json = QuineJson,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) : LlmProvider {
    private val chunkParser = OpenAiChunkParser(json)

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: throw LlmException(LlmErrors.missingKey())
        val payload = OpenAiRequestBuilder.build(
            json = json,
            request = request.copy(model = request.model.ifBlank { model }),
        )

        var attemptsDone = 0
        var emittedAnything = false
        while (true) {
            try {
                emitAll(streamOnce(payload, apiKey).onEach { emittedAnything = true })
                return@flow
            } catch (error: LlmException) {
                if (!emittedAnything && retryPolicy.shouldRetry(error.error, attemptsDone)) {
                    delay(retryPolicy.delayMillis(attemptsDone))
                    attemptsDone++
                    continue
                }
                throw error
            }
        }
    }.flowOn(dispatcher)

    override suspend fun checkKey(): Result<Unit> = withContext(dispatcher) {
        val apiKey = apiKeyProvider()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(LlmException(LlmErrors.missingKey()))
        try {
            client.newCall(
                Request.Builder()
                    .url(endpoint("models"))
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build(),
            ).execute().use { response ->
                when {
                    response.isSuccessful -> Result.success(Unit)
                    response.code == 404 || response.code == 405 -> probeChat(apiKey)
                    else -> Result.failure(
                        LlmException(LlmErrors.http(response.code, response.body?.string().orEmpty())),
                    )
                }
            }
        } catch (error: IOException) {
            Result.failure(LlmException(LlmErrors.network(error)))
        }
    }

    /** 有些兼容服务没有 /models 端点：用一次 1 token 的最小请求验证 Key。 */
    private fun probeChat(apiKey: String): Result<Unit> {
        val body = OpenAiRequestBuilder.build(
            json = json,
            request = LlmRequest(
                model = model,
                messages = listOf(LlmMessage(ChatRole.USER, "ping")),
                maxTokens = 1,
            ),
            stream = false,
        )
        return try {
            client.newCall(
                Request.Builder()
                    .url(endpoint("chat/completions"))
                    .header("Authorization", "Bearer $apiKey")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
            ).execute().use { response ->
                when {
                    response.isSuccessful -> Result.success(Unit)
                    // 400/422：鉴权已通过，只是模型名或参数不合口味
                    response.code == 400 || response.code == 422 -> Result.success(Unit)
                    else -> Result.failure(
                        LlmException(LlmErrors.http(response.code, response.body?.string().orEmpty())),
                    )
                }
            }
        } catch (error: IOException) {
            Result.failure(LlmException(LlmErrors.network(error)))
        }
    }

    private fun streamOnce(payload: String, apiKey: String): Flow<LlmEvent> = flow {
        val request = Request.Builder()
            .url(endpoint("chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "text/event-stream")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            throw LlmException(LlmErrors.network(error))
        }

        response.use { http ->
            if (!http.isSuccessful) {
                val text = http.body?.string().orEmpty().take(2000)
                throw LlmException(LlmErrors.http(http.code, text))
            }
            val body = http.body
                ?: throw LlmException(
                    LlmErrors.server(
                        message = "模型服务返回了空响应。",
                        impact = "这一条没有生成回复。",
                        nextStep = "点重试；若一直是空的，换一个模型试试。",
                    ),
                )
            val contentType = body.contentType()?.toString().orEmpty()
            if (!contentType.contains("event-stream", ignoreCase = true)) {
                // 忽略 stream 参数的服务：按完整响应解析
                chunkParser.parseFull(body.string()).forEach { emit(it) }
                return@use
            }
            val source = body.source()
            val decoder = SseFrameDecoder()
            while (true) {
                val line = source.readUtf8Line() ?: break
                val frame = decoder.onLine(line) ?: continue
                if (frame.trim() == OpenAiChunkParser.DONE) {
                    emit(LlmEvent.Finish("done"))
                    break
                }
                chunkParser.parse(frame).forEach { emit(it) }
            }
            decoder.flush()?.let { frame ->
                if (frame.trim() != OpenAiChunkParser.DONE) {
                    chunkParser.parse(frame).forEach { emit(it) }
                }
            }
        }
    }

    private fun endpoint(path: String): String = baseUrl.trimEnd('/') + "/" + path

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** 日志里可以安全打印的地址。 */
        fun safeEndpoint(baseUrl: String): String = LogRedaction.redact(baseUrl)
    }
}
