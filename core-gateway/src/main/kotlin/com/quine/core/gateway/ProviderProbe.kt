package com.quine.core.gateway

/**
 * 连通性探测：用给定配置 + 临时 Key 探一次，不落库。
 *
 * 首启屏与设置页共用同一份实现，避免两处各写一遍（也就不会两处各错一遍）。
 * 这里刻意关掉 provider 内部重试 —— 探测要的是「快而准」，不是「倔」。
 */
class ProviderProbe(
    private val factory: OpenAiProviderFactory = OpenAiProviderFactory(
        retryPolicy = RetryPolicy(maxRetries = 0),
    ),
) {
    suspend fun check(config: ProviderConfig, apiKey: String): Result<Unit> {
        if (config.baseUrl.isBlank()) {
            return Result.failure(
                LlmException(
                    LlmErrors.server(
                        message = "还没填 baseUrl。",
                        impact = "连不上模型服务。",
                        nextStep = "去 设置 → 模型 选一个供应商，或自己填上接口地址。",
                    ),
                ),
            )
        }
        if (config.model.isBlank()) {
            return Result.failure(
                LlmException(
                    LlmErrors.server(
                        message = "还没填模型名。",
                        impact = "连不上模型服务。",
                        nextStep = "去 设置 → 模型 填一个模型名，例如 deepseek-chat。",
                    ),
                ),
            )
        }
        if (apiKey.isBlank()) return Result.failure(LlmException(LlmErrors.missingKey()))

        return runCatching {
            factory.create(
                baseUrl = config.baseUrl,
                model = config.model,
                apiKeyProvider = { apiKey },
            ).checkKey().getOrThrow()
        }
    }
}
