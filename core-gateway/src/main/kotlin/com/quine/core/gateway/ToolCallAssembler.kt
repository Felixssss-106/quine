package com.quine.core.gateway

import com.quine.core.common.Ids

/**
 * 把流式返回的 tool_call 增量拼成完整调用。
 * 部分服务把 id / name / arguments 分散在多个 chunk 里，index 是唯一稳定的键。
 */
class ToolCallAssembler {
    private class Partial {
        val id = StringBuilder()
        val name = StringBuilder()
        val arguments = StringBuilder()
    }

    private val parts = sortedMapOf<Int, Partial>()

    fun accept(delta: LlmEvent.ToolCallDelta) {
        val part = parts.getOrPut(delta.index) { Partial() }
        delta.id?.takeIf { it.isNotBlank() }?.let { if (part.id.isEmpty()) part.id.append(it) }
        delta.name?.takeIf { it.isNotBlank() }?.let { part.name.append(it) }
        delta.argumentsDelta?.let { part.arguments.append(it) }
    }

    val hasCalls: Boolean get() = parts.values.any { it.name.isNotEmpty() }

    fun build(): List<ToolCallRequest> = parts.values
        .filter { it.name.isNotEmpty() }
        .map { part ->
            ToolCallRequest(
                id = part.id.toString().ifBlank { Ids.new() },
                name = part.name.toString(),
                argumentsJson = part.arguments.toString().ifBlank { "{}" },
            )
        }
}
