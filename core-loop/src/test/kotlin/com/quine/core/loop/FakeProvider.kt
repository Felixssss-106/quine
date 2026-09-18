package com.quine.core.loop

import com.quine.core.common.QuineError
import com.quine.core.gateway.LlmEvent
import com.quine.core.gateway.LlmException
import com.quine.core.gateway.LlmProvider
import com.quine.core.gateway.LlmRequest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 假 provider：按脚本逐轮产出事件，并记录每次收到的请求。
 * 用它就能把 loop 的五条路径（正常 / 工具 / 重试 / 取消 / 失败）在纯 JVM 上跑完。
 */
class FakeProvider : LlmProvider {

    sealed interface Turn {
        /** 一次性吐完这些事件然后正常结束。 */
        data class Emit(val events: List<LlmEvent>) : Turn

        /** 抛出网关错误。 */
        data class Fail(val error: QuineError) : Turn

        /** 先吐几个事件，再抛错 —— 用来验证「已经吐过字就不重放」。 */
        data class EmitThenFail(val events: List<LlmEvent>, val error: QuineError) : Turn

        /** 先吐几个事件，然后永远挂起 —— 用来测取消。 */
        data class Hang(val events: List<LlmEvent>) : Turn
    }

    private val script = ArrayDeque<Turn>()

    /** 每次 `stream()` 收到的请求，按顺序记录。 */
    val requests: MutableList<LlmRequest> = mutableListOf()

    var checkKeyResult: Result<Unit> = Result.success(Unit)

    fun enqueue(turn: Turn): FakeProvider = apply { script.addLast(turn) }

    override fun stream(request: LlmRequest): Flow<LlmEvent> = flow {
        requests += request
        when (val turn = script.removeFirstOrNull() ?: Turn.Emit(emptyList())) {
            is Turn.Emit -> turn.events.forEach { emit(it) }
            is Turn.Fail -> throw LlmException(turn.error)
            is Turn.EmitThenFail -> {
                turn.events.forEach { emit(it) }
                throw LlmException(turn.error)
            }
            is Turn.Hang -> {
                turn.events.forEach { emit(it) }
                awaitCancellation()
            }
        }
    }

    override suspend fun checkKey(): Result<Unit> = checkKeyResult
}
