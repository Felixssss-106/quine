package com.quine.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.quine.core.common.QuineError
import com.quine.core.common.TrustLevel
import com.quine.core.design.component.QuineBreathingDot
import com.quine.core.design.component.QuineDivider
import com.quine.core.design.component.QuineStreamingCursor
import com.quine.core.design.component.QuineTrustPill
import com.quine.core.design.theme.QuineTheme
import com.quine.core.storage.ChatMessage

@Composable
fun ChatRoute(
    deps: ChatDeps,
    onOpenSettings: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(deps))
    val state by viewModel.state.collectAsStateWithLifecycle()

    ChatScreen(
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onRetry = viewModel::retry,
        onEdit = viewModel::editLast,
        onToggleActivity = viewModel::toggleActivity,
        onDismissError = viewModel::dismissError,
        onTrustLevel = viewModel::setTrustLevel,
        onOpenSettings = onOpenSettings,
        onOpenTasks = onOpenTasks,
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onToggleActivity: () -> Unit,
    onDismissError: () -> Unit,
    onTrustLevel: (TrustLevel) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding(),
    ) {
        ChatTopBar(onOpenSettings = onOpenSettings, onOpenTasks = onOpenTasks)
        QuineDivider()

        MessageList(
            state = state,
            onToggleActivity = onToggleActivity,
            modifier = Modifier.weight(1f),
        )

        if (state.error != null || state.stopped) {
            StatusBar(
                error = state.error,
                stopped = state.stopped,
                onRetry = onRetry,
                onEdit = onEdit,
                onDismiss = onDismissError,
            )
        }

        Composer(
            draft = state.draft,
            streaming = state.streaming,
            trustLevel = state.trustLevel,
            onDraftChange = onDraftChange,
            onSend = onSend,
            onStop = onStop,
            onTrustLevel = onTrustLevel,
        )
    }
}

@Composable
private fun ChatTopBar(onOpenSettings: () -> Unit, onOpenTasks: () -> Unit) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = dimens.pageMargin, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Quine",
            style = QuineTheme.typography.headline,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenTasks) {
            Text(
                text = "任务",
                style = QuineTheme.typography.footnote,
                color = colors.textSecondary,
            )
        }
        TextButton(onClick = onOpenSettings) {
            Text(
                text = "设置",
                style = QuineTheme.typography.footnote,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MessageList(
    state: ChatUiState,
    onToggleActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val visible = state.visibleMessages
    val live = state.live
    val total = visible.size + if (live != null) 1 else 0

    // 首帧不滚 —— 这样旋转 / 杀进程恢复出来的滚动位置不会被覆盖（「状态永不丢」）。
    var lastTotal by remember { mutableIntStateOf(-1) }
    LaunchedEffect(total, live?.text?.length) {
        if (lastTotal == -1) {
            lastTotal = total
            return@LaunchedEffect
        }
        if (total > 0) listState.scrollToItem(total - 1)
        lastTotal = total
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = QuineTheme.dimens.pageMargin,
            vertical = QuineTheme.dimens.sectionGap,
        ),
        verticalArrangement = Arrangement.spacedBy(QuineTheme.dimens.sectionGap),
    ) {
        items(items = visible, key = { it.id }) { message ->
            MessageRow(message = message, onToggleActivity = onToggleActivity)
        }
        if (live != null) {
            item(key = "live-turn") { LiveRow(live = live, onToggleActivity = onToggleActivity) }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage, onToggleActivity: () -> Unit) {
    when {
        message.isUser -> UserBubble(text = message.text)
        message.isTool -> ActivityCardRow(
            card = message.toActivityCard(),
            onToggle = onToggleActivity,
        )

        message.isAssistant -> if (message.text.isNotBlank()) {
            AssistantText(text = message.text)
        }

        else -> Unit
    }
}

@Composable
private fun LiveRow(live: LiveTurn, onToggleActivity: () -> Unit) {
    Column {
        if (live.text.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Bottom) {
                AssistantText(
                    text = live.text,
                    modifier = Modifier.weight(1f, fill = false),
                    fillWidth = false,
                )
                Spacer(Modifier.width(4.dp))
                QuineStreamingCursor()
            }
        }
        if (live.activity != null) {
            if (live.text.isNotEmpty()) Spacer(Modifier.height(12.dp))
            ActivityCardRow(card = live.activity, onToggle = onToggleActivity)
        }
        if (live.text.isEmpty() && live.activity == null) {
            // 刚开始、还没有任何内容：给一个呼吸点，别让界面看起来卡住了。
            QuineBreathingDot()
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxBubble = maxWidth * 0.82f
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxBubble)
                    .clip(RoundedCornerShape(dimens.radiusBubble))
                    .background(colors.fill)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = text,
                    style = QuineTheme.typography.body,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

/** AI 消息 = 裸文本平铺（docs/visual-spec.md §4-B），Markdown 渲染。 */
@Composable
private fun AssistantText(
    text: String,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    Markdown(
        content = text,
        colors = markdownColor(),
        typography = markdownTypography(),
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
    )
}

@Composable
private fun ActivityCardRow(card: ActivityCard, onToggle: () -> Unit) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography
    val motion = QuineTheme.motion
    val reduced = QuineTheme.reducedMotion

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusCard))
            .background(colors.fill)
            .clickable(onClick = onToggle)
            .padding(dimens.cardPadding)
            .animateContentSize(animationSpec = motion.softSpring()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (card.running) {
                QuineBreathingDot()
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (card.ok) colors.textSecondary else colors.danger),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = card.summary,
                style = typography.footnote,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (card.expanded) "收起" else "展开",
                style = typography.caption,
                color = colors.textTertiary,
            )
        }

        AnimatedVisibility(
            visible = card.expanded,
            enter = fadeIn(tween(motion.duration(motion.standard, reduced))) +
                expandVertically(animationSpec = motion.softSpring()),
            exit = fadeOut(tween(motion.duration(motion.micro, reduced))) +
                shrinkVertically(animationSpec = motion.standardSpring()),
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                if (card.arguments.isNotBlank()) {
                    Text(
                        text = card.arguments,
                        style = typography.monoData,
                        color = colors.textTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (card.detail.isNotBlank()) {
                    Text(
                        text = card.detail.take(MAX_DETAIL_CHARS),
                        style = typography.monoTerminal,
                        color = colors.textSecondary,
                    )
                }
                card.sourceRef?.let { ref ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "来源：$ref",
                        style = typography.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

/** 错误条 / 已停止条（docs/page-specs.md §0：每个错误文案必须带下一步动作）。 */
@Composable
private fun StatusBar(
    error: QuineError?,
    stopped: Boolean,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography

    val message = error?.message ?: "已停止。"
    val impact = error?.impact ?: "这次生成被中断，已完成的部分还在。"
    val nextStep = error?.nextStep ?: "可以重新发送，或先改一改再发。"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pageMargin, vertical = 8.dp)
            .clip(RoundedCornerShape(dimens.radiusCard))
            .background(colors.fill)
            .padding(dimens.cardPadding),
    ) {
        Text(text = message, style = typography.footnote, color = colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(text = impact, style = typography.caption, color = colors.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text(text = nextStep, style = typography.caption, color = colors.textSecondary)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRetry) {
                Text("重试", style = typography.footnote, color = colors.accent)
            }
            TextButton(onClick = onEdit) {
                Text("编辑", style = typography.footnote, color = colors.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text("收起", style = typography.footnote, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    streaming: Boolean,
    trustLevel: TrustLevel,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onTrustLevel: (TrustLevel) -> Unit,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val typography = QuineTheme.typography

    var focused by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimens.pageMargin,
                end = dimens.pageMargin,
                top = 8.dp,
                bottom = dimens.composerBottomInset,
            ),
    ) {
        // 模式行：档位胶囊常驻（docs/page-specs.md §3）
        QuineTrustPill(
            labels = TrustLevel.entries.map { it.label },
            selectedIndex = trustLevel.ordinal,
            onSelect = { onTrustLevel(TrustLevel.entries[it]) },
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundActionButton(
                contentDescription = "更多",
                onClick = { notice = "附件与「从别的 App 分享进来」在后续版本上线。" },
            ) {
                PlusGlyph(color = colors.onInk)
            }

            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                textStyle = typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                minLines = 1,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dimens.radiusInput))
                            .background(colors.fill)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        if (draft.isEmpty()) {
                            Text(
                                text = if (focused) {
                                    "描述任务、粘贴东西、说要改哪个文件"
                                } else {
                                    "让它干点什么…"
                                },
                                style = typography.body,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )

            RoundActionButton(
                contentDescription = when {
                    streaming -> "停止"
                    draft.isBlank() -> "按住说话"
                    else -> "发送"
                },
                onClick = {
                    when {
                        streaming -> onStop()
                        draft.isBlank() -> notice = "语音输入在后续版本上线。"
                        else -> onSend()
                    }
                },
            ) {
                when {
                    streaming -> StopGlyph(color = colors.onInk)
                    draft.isBlank() -> MicGlyph(color = colors.onInk)
                    else -> SendGlyph(color = colors.onInk)
                }
            }
        }

        notice?.let { text ->
            Spacer(Modifier.height(8.dp))
            Text(text = text, style = typography.caption, color = colors.textTertiary)
            LaunchedEffect(text) {
                kotlinx.coroutines.delay(4000)
                notice = null
            }
        }
    }
}

@Composable
private fun RoundActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = QuineTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.ink)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ---- 手绘图形：不引图标库，形状完全可控（设计纪律：圆角即语言、无渐变） ----

@Composable
private fun SendGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        val cx = size.width / 2f
        val top = size.height * 0.2f
        drawLine(color, Offset(cx, size.height * 0.82f), Offset(cx, top), stroke, StrokeCap.Round)
        drawLine(
            color,
            Offset(cx, top),
            Offset(cx - size.width * 0.28f, size.height * 0.5f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(cx, top),
            Offset(cx + size.width * 0.28f, size.height * 0.5f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun StopGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val side = size.minDimension * 0.52f
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
            size = Size(side, side),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    }
}

@Composable
private fun MicGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height

        val capsuleWidth = w * 0.34f
        drawRoundRect(
            color = color,
            topLeft = Offset((w - capsuleWidth) / 2f, h * 0.1f),
            size = Size(capsuleWidth, h * 0.5f),
            cornerRadius = CornerRadius(capsuleWidth / 2f),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.34f),
            size = Size(w * 0.64f, h * 0.44f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = color,
            Offset(w / 2f, h * 0.78f),
            Offset(w / 2f, h * 0.94f),
            stroke,
            StrokeCap.Round,
        )
    }
}

@Composable
private fun PlusGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color,
            Offset(size.width * 0.2f, size.height / 2f),
            Offset(size.width * 0.8f, size.height / 2f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(size.width / 2f, size.height * 0.2f),
            Offset(size.width / 2f, size.height * 0.8f),
            stroke,
            StrokeCap.Round,
        )
    }
}

/** 活动卡展开时最多展示多少字符 —— 终端块不需要把 64 KB 全铺出来。 */
private const val MAX_DETAIL_CHARS = 4000

private fun ChatMessage.toActivityCard(): ActivityCard = ActivityCard(
    callId = toolCallId.orEmpty(),
    toolId = toolName.orEmpty(),
    title = toolName.orEmpty(),
    summary = meta.summary ?: ActivitySummary.describe(
        toolId = toolName.orEmpty(),
        argumentsJson = meta.arguments.orEmpty(),
        ok = meta.ok ?: true,
        truncated = meta.truncated,
    ),
    detail = text,
    arguments = meta.arguments.orEmpty(),
    running = false,
    ok = meta.ok ?: true,
    sourceRef = meta.sourceRef,
    truncated = meta.truncated,
)
