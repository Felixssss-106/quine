package com.quine.core.tools

import com.quine.core.common.ErrorKind
import com.quine.core.common.QuineError
import com.quine.core.gateway.ToolSchema
import com.quine.core.tools.schema.FieldError
import com.quine.core.tools.schema.JsonSchemaValidator
import com.quine.core.tools.schema.SchemaValidation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 工具注册表：参数校验 + 统一执行入口。
 *
 * **校验失败不抛异常**，而是返回一个 `ok = false` 的 [ToolResult]：
 * 错误文本会作为工具结果回灌给模型，让它自己改参数重试 —— 这是 agent loop
 * 能自纠的关键，也是「不静默失败」在工具层的体现。
 */
class ToolRegistry(tools: List<Tool>) {

    private val all: List<Tool> = tools
    private val byId: Map<String, Tool> = tools.associateBy { it.spec.id }

    /** 发给模型的工具声明。 */
    fun schemas(): List<ToolSchema> = all.map { it.spec.toSchema() }

    fun find(id: String): Tool? = byId[id]

    val size: Int get() = all.size

    val ids: Set<String> get() = byId.keys

    /**
     * 唯一的工具执行入口：查表 → 解析参数 → 校验 → 检查取消 → 执行。
     *
     * @param argumentsJson 模型给的原始参数串（可能是空串或半截 JSON）。
     */
    suspend fun execute(
        toolId: String,
        argumentsJson: String,
        context: ToolContext,
    ): ToolResult {
        val tool = byId[toolId] ?: return failure(
            toolId = toolId,
            error = QuineError(
                kind = ErrorKind.TOOL,
                message = "没有 $toolId 这个工具。",
                impact = "这次工具调用没有执行。",
                nextStep = "换一个已提供的工具，或直接用文字回答。",
            ),
        )

        val args = parse(argumentsJson)
        if (args == null) {
            return failure(
                toolId = toolId,
                error = QuineError(
                    kind = ErrorKind.TOOL,
                    message = "$toolId 的参数不是合法 JSON。",
                    impact = "这次工具调用没有执行。",
                    nextStep = "把参数写成一个 JSON 对象，例如 {\"path\":\"notes/todo.md\"}。",
                ),
            )
        }

        when (val validation = JsonSchemaValidator.validate(tool.spec.parameters, args)) {
            is SchemaValidation.Invalid -> return failure(
                toolId = toolId,
                error = QuineError(
                    kind = ErrorKind.TOOL,
                    message = "$toolId 的参数不对：${describe(validation.errors)}",
                    impact = "这次工具调用没有执行。",
                    nextStep = "按提示改好参数再调用一次。",
                ),
            )

            SchemaValidation.Valid -> Unit
        }

        if (context.cancelled()) {
            return failure(
                toolId = toolId,
                error = QuineError.cancelled(),
            )
        }

        return tool.execute(args, context)
    }

    private fun parse(raw: String): JsonObject? {
        val text = raw.trim()
        if (text.isEmpty()) return JsonObject(emptyMap())
        return runCatching { LenientJson.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    private fun describe(errors: List<FieldError>): String =
        errors.joinToString("；") { "${it.path} ${it.message}" }

    private fun failure(toolId: String, error: QuineError): ToolResult = ToolResult(
        toolId = toolId,
        ok = false,
        output = error.message + error.nextStep,
        error = error,
    )

    private companion object {
        val LenientJson = Json { isLenient = true; ignoreUnknownKeys = true }
    }
}
