package com.quine.core.gateway

import com.quine.core.common.ReasoningEffort
import kotlinx.serialization.json.JsonObject

enum class ChatRole { SYSTEM, USER, ASSISTANT, TOOL }

/** 模型要求调用的一次工具。 */
data class ToolCallRequest(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class LlmMessage(
    val role: ChatRole,
    val content: String? = null,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
)

/** 暴露给模型的工具声明（JSON Schema）。 */
data class ToolSchema(
    val id: String,
    val description: String,
    val parameters: JsonObject,
)

data class LlmRequest(
    val model: String = "",
    val messages: List<LlmMessage>,
    val tools: List<ToolSchema> = emptyList(),
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    /** 思考等级；`关闭` 时该参数不会出现在请求体里（见 [ReasoningEffort.wireValue]）。 */
    val reasoningEffort: ReasoningEffort? = null,
)

sealed interface LlmEvent {
    data class TextDelta(val text: String) : LlmEvent

    data class ToolCallDelta(
        val index: Int,
        val id: String?,
        val name: String?,
        val argumentsDelta: String?,
    ) : LlmEvent

    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int,
    ) : LlmEvent

    data class Finish(val reason: String?) : LlmEvent
}

/** 模型供应商配置（Key 只以引用形式存在，明文在 Keystore）。 */
data class ProviderConfig(
    val presetId: String = "deepseek",
    val displayName: String = "DeepSeek",
    val baseUrl: String = "",
    val model: String = "",
    val apiKeyRef: String = DEFAULT_API_KEY_REF,
    val isCustom: Boolean = false,
) {
    companion object {
        const val DEFAULT_API_KEY_REF = "default"
    }
}
