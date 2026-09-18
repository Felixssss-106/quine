package com.quine.core.common

/** 失败分类：决定文案与是否值得重试。 */
enum class ErrorKind {
    NETWORK,
    AUTH,
    RATE_LIMIT,
    CONTENT_POLICY,
    SERVER,
    TOOL,
    STORAGE,
    CANCELLED,
    UNKNOWN,
}

/**
 * 统一错误模型。文案纪律（docs/page-specs.md §0）：
 * 每个错误都必须讲清「发生了什么 + 影响 + 你可以怎么办」。
 */
data class QuineError(
    val kind: ErrorKind,
    val message: String,
    val impact: String,
    val nextStep: String,
    val cause: Throwable? = null,
) {
    /** 一行内联展示用（错误条）。 */
    val inline: String get() = "$message$nextStep"

    companion object {
        fun unknown(cause: Throwable? = null): QuineError = QuineError(
            kind = ErrorKind.UNKNOWN,
            message = "出了点问题。",
            impact = "这次操作没有完成。",
            nextStep = "再试一次；若反复失败，去设置里检查模型配置。",
            cause = cause,
        )

        fun cancelled(): QuineError = QuineError(
            kind = ErrorKind.CANCELLED,
            message = "已停止。",
            impact = "这次生成被中断，已完成的部分还在。",
            nextStep = "可以重新发送，或先改一改再发。",
        )

        fun storage(message: String, cause: Throwable? = null): QuineError = QuineError(
            kind = ErrorKind.STORAGE,
            message = message,
            impact = "这次改动没有保存。",
            nextStep = "检查存储空间后重试。",
            cause = cause,
        )
    }
}
