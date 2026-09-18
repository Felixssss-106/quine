package com.quine.core.gateway

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient

/** 按当前配置创建 provider；M0 只走 OpenAI 兼容实现。 */
class OpenAiProviderFactory(
    private val client: OkHttpClient = OpenAiCompatProvider.defaultClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    fun create(
        baseUrl: String,
        model: String,
        apiKeyProvider: suspend () -> String?,
    ): LlmProvider = OpenAiCompatProvider(
        baseUrl = baseUrl,
        model = model,
        apiKeyProvider = apiKeyProvider,
        client = client,
        dispatcher = dispatcher,
        retryPolicy = retryPolicy,
    )
}
