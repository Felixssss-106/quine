package com.quine.core.tools

import com.quine.core.common.QuineError
import com.quine.core.common.SystemTime
import com.quine.core.common.TimeProvider
import com.quine.core.gateway.ToolSchema
import com.quine.core.tools.workspace.Workspace
import kotlinx.serialization.json.JsonObject

/**
 * 危险等级（agent-prompt.md §2.7）：决定是否拦截。
 * M0 只做声明；审批矩阵（权限模式 × 危险级）在 M1 生效。
 */
enum class RiskLevel {
    /** 安全：沙箱 / 工作区内。 */
    SAFE,

    /** 敏感：设置、通知、剪贴板。 */
    SENSITIVE,

    /** 高危：删数据、花钱、发消息、装卸应用。 */
    DANGEROUS,
}

/** 能力等级（agent-prompt.md §2.7）：运行时检测可用性，按可用性逐级升级。 */
enum class CapabilityLevel {
    /** 标准 Android API。 */
    STANDARD,

    /** Shizuku。 */
    SHIZUKU,

    /** Root（v1 后置）。 */
    ROOT,

    /** 无障碍。 */
    ACCESSIBILITY,
}

/**
 * 工具声明。`parameters` 是 JSON Schema，直接发给模型。
 */
data class ToolSpec(
    val id: String,
    val title: String,
    val description: String,
    val parameters: JsonObject,
    val risk: RiskLevel = RiskLevel.SAFE,
    val capability: CapabilityLevel = CapabilityLevel.STANDARD,
)

/**
 * 工具执行结果。**截断必须带引用**（agent-prompt.md §2.3）：
 * `truncated = true` 时 `sourceRef` 不可为空，否则模型无法知道被省略了什么。
 */
data class ToolResult(
    val toolId: String,
    val ok: Boolean,
    val output: String,
    val sourceRef: String? = null,
    val truncated: Boolean = false,
    val error: QuineError? = null,
    val durationMillis: Long = 0,
)

/**
 * 工具执行上下文。
 *
 * `snapshots` **没有默认值**：写类工具改动前必须能拿到快照，
 * 给默认值会让调用方在不知不觉中跳过这条红线。
 */
data class ToolContext(
    val workspace: Workspace,
    val snapshots: SnapshotRegistry,
    val cancelled: () -> Boolean = { false },
    val time: TimeProvider = SystemTime,
)

/** 内置工具的统一契约。 */
interface Tool {
    val spec: ToolSpec

    suspend fun execute(args: JsonObject, context: ToolContext): ToolResult
}

/** 把 [ToolSpec] 转成发给模型的声明。 */
fun ToolSpec.toSchema(): ToolSchema = ToolSchema(
    id = id,
    description = description,
    parameters = parameters,
)
