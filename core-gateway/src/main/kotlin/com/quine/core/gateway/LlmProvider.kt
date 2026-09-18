package com.quine.core.gateway

import kotlinx.coroutines.flow.Flow

/**
 * 模型网关统一接口。M0 只有 OpenAI 兼容实现；
 * Anthropic / Gemini 原生适配器在 M2 按同一接口补齐。
 */
interface LlmProvider {
    /** 流式对话；工具调用以增量事件输出。 */
    fun stream(request: LlmRequest): Flow<LlmEvent>

    /** 校验 Key / baseUrl / 模型名是否可用。 */
    suspend fun checkKey(): Result<Unit>
}
