package com.quine.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.quine.core.storage.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 详情页：头卡数据 + 产物区。 */
data class TaskDetailUiState(
    val run: TaskRunUi? = null,
    val artifacts: List<ArtifactUi> = emptyList(),
)

/** 列表页按状态分组（page-specs §2：运行中 / 需确认 / 完成 / 失败）。 */
data class TasksUiState(
    val running: List<TaskRunUi> = emptyList(),
    val needsApproval: List<TaskRunUi> = emptyList(),
    val finished: List<TaskRunUi> = emptyList(),
    val failed: List<TaskRunUi> = emptyList(),
) {
    val isEmpty: Boolean get() = running.isEmpty() && needsApproval.isEmpty() &&
        finished.isEmpty() && failed.isEmpty()
}

class TasksViewModel(private val deps: TasksDeps) : ViewModel() {

    val state: StateFlow<TasksUiState> = deps.observeRuns()
        .map { runs ->
            val ui = runs.map { it.toUi { id -> deps.conversationTitle(id) } }
            TasksUiState(
                running = ui.filter { it.status == TaskStatus.RUNNING || it.status == TaskStatus.QUEUED },
                needsApproval = ui.filter { it.status == TaskStatus.NEEDS_APPROVAL },
                finished = ui.filter { it.status == TaskStatus.SUCCEEDED },
                failed = ui.filter { it.status == TaskStatus.FAILED || it.status == TaskStatus.CANCELLED },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    /** 详情页的时间线。 */
    fun timeline(taskId: String): Flow<List<TimelineItemUi>> =
        deps.observeSteps(taskId).map { steps -> steps.map { it.toTimelineItem() } }

    private val _detail = MutableStateFlow(TaskDetailUiState())
    val detail: StateFlow<TaskDetailUiState> = _detail

    fun loadDetail(taskId: String) {
        viewModelScope.launch {
            val run = deps.findRun(taskId) ?: return@launch
            _detail.value = TaskDetailUiState(
                run = run.toUi { id -> deps.conversationTitle(id) },
                artifacts = deps.artifacts(taskId),
            )
        }
    }

    /** 操作条的「停止」。 */
    fun cancel(taskId: String) {
        viewModelScope.launch { deps.cancel(taskId) }
    }

    companion object {
        fun factory(deps: TasksDeps): ViewModelProvider.Factory = viewModelFactory {
            initializer { TasksViewModel(deps) }
        }
    }
}
