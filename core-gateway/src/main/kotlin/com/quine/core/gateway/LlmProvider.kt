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

    /**
     * 拉取可用模型 id 列表（按字典序）。
     *
     * 失败时返回 [Result.failure]，并带上可直接展示的三段式错误 —— 调用方不需要
     * 自己翻译异常。没有 /models 端点的兼容服务也算失败，因为那时确实拿不到列表。
     */
    suspend fun models(): Result<List<String>>
}
