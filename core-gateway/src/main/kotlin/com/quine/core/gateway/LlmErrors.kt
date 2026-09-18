package com.quine.core.gateway

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.IOException

/**
 * 错误映射：网络 / 鉴权 / 限流 / 内容策略 / 服务端 → 统一人话错误。
 * 文案纪律：每条都必须带下一步动作（docs/page-specs.md §0）。
 */
object LlmErrors {
    fun missingKey(): QuineError = QuineError(
        kind = ErrorKind.AUTH,
        message = "还没有接上模型。",
        impact = "这条消息发不出去。",
        nextStep = "去 设置 → 模型 粘贴 API Key，或先换一个供应商。",
    )

    fun http(code: Int, body: String): QuineError = when (code) {
        401, 403 -> QuineError(
            kind = ErrorKind.AUTH,
            message = "模型服务说这个 Key 不认。",
            impact = "这条消息没有发出去。",
            nextStep = "去 设置 → 模型 检查 Key 是否粘贴完整、是否过期。",
        )

        429 -> QuineError(
            kind = ErrorKind.RATE_LIMIT,
            message = "模型服务说请求太频繁。",
            impact = "这一条暂时没发出去。",
            nextStep = "等十几秒再点重试；长期这样就去供应商后台看看额度。",
        )

        400, 404, 422 -> if (looksLikeContentPolicy(body)) {
            QuineError(
                kind = ErrorKind.CONTENT_POLICY,
                message = "模型服务拒绝了这段内容。",
                impact = "这一条没有生成回复。",
                nextStep = "换一种说法再发，或换一个模型试试。",
            )
        } else {
            QuineError(
                kind = ErrorKind.SERVER,
                message = "模型服务说这个请求不合法（$code）。",
                impact = "这一条没有生成回复。",
                nextStep = "检查设置里的模型名和 baseUrl 是否是供应商推荐的写法。",
            )
        }

        in 500..599 -> QuineError(
            kind = ErrorKind.SERVER,
            message = "模型服务自己出错了（$code）。",
            impact = "这一条没有生成回复。",
            nextStep = "等一会儿点重试；一直失败可以去供应商状态页看看。",
        )

        else -> QuineError(
            kind = ErrorKind.SERVER,
            message = "模型服务返回了意外的状态（$code）。",
            impact = "这一条没有生成回复。",
            nextStep = "点重试；若反复出现，把 baseUrl 换成供应商文档里的官方地址。",
        )
    }

    fun network(cause: IOException): QuineError = QuineError(
        kind = ErrorKind.NETWORK,
        message = "网络没连上模型服务。",
        impact = "这条消息没有发出去。",
        nextStep = "检查网络或代理，然后点重试。",
        cause = cause,
    )

    fun malformed(): QuineError = QuineError(
        kind = ErrorKind.SERVER,
        message = "模型服务返回了看不懂的内容。",
        impact = "这一条没有生成回复。",
        nextStep = "点重试；若一直这样，换一个模型或换回官方 baseUrl。",
    )

    fun server(message: String, impact: String, nextStep: String): QuineError =
        QuineError(ErrorKind.SERVER, message, impact, nextStep)

    /** 从 `{"error": {...}}` 负载里抽人话。 */
    fun providerPayload(json: Json, element: JsonElement): QuineError {
        val raw = runCatching {
            val obj = element.jsonObjectOrNull()
            (obj?.get("message")?.asStringOrNull() ?: obj?.get("msg")?.asStringOrNull())
                ?: json.encodeToString(JsonElement.serializer(), element)
        }.getOrNull().orEmpty()
        val message = raw.take(160)
        val kind = when {
            message.contains("key", true) || message.contains("auth", true) -> ErrorKind.AUTH
            message.contains("quota", true) || message.contains("rate", true) -> ErrorKind.RATE_LIMIT
            message.contains("content", true) || message.contains("safety", true) -> ErrorKind.CONTENT_POLICY
            else -> ErrorKind.SERVER
        }
        return QuineError(
            kind = kind,
            message = "模型服务拒绝了请求：$message",
            impact = "这一条没有生成回复。",
            nextStep = "按提示改一下（多为 Key、额度或内容），再点重试。",
        )
    }

    private fun looksLikeContentPolicy(body: String): Boolean {
        val lower = body.lowercase()
        val mentions = lower.contains("content") || lower.contains("policy") ||
            lower.contains("safety") || lower.contains("filter") || lower.contains("moderation")
        return mentions && !lower.contains("api key")
    }
}
