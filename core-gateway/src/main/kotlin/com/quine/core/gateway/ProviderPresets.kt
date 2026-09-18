package com.quine.core.gateway

/** 内置供应商预设；Key 一律只存在本机 Keystore。 */
data class ProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val isCustom: Boolean = false,
)

object ProviderPresets {
    val all: List<ProviderPreset> = listOf(
        ProviderPreset(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            defaultModel = "deepseek-chat",
        ),
        ProviderPreset(
            id = "kimi",
            displayName = "Kimi",
            baseUrl = "https://api.moonshot.cn/v1",
            defaultModel = "moonshot-v1-8k",
        ),
        ProviderPreset(
            id = "glm",
            displayName = "智谱 GLM",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-plus",
        ),
        ProviderPreset(
            id = "qwen",
            displayName = "通义千问",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
        ),
        ProviderPreset(
            id = "openai",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
        ),
        ProviderPreset(
            id = "custom",
            displayName = "自定义",
            baseUrl = "",
            defaultModel = "",
            isCustom = true,
        ),
    )

    fun byId(id: String?): ProviderPreset =
        all.firstOrNull { it.id == id } ?: all.first()
}

/** 把预设铺开成一份可直接使用的配置（baseUrl 与 model 都填好）。 */
fun ProviderPreset.toConfig(
    apiKeyRef: String = ProviderConfig.DEFAULT_API_KEY_REF,
): ProviderConfig = ProviderConfig(
    presetId = id,
    displayName = displayName,
    baseUrl = baseUrl,
    model = defaultModel,
    apiKeyRef = apiKeyRef,
    isCustom = isCustom,
)
