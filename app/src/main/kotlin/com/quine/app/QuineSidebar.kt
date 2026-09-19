package com.quine.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quine.core.design.theme.QuineTheme

/**
 * 横屏侧边栏：会话与任务两个面板，可切换、可收起。
 *
 * 这里只画界面，数据由 app 层传进来 —— 侧边栏同时用到会话表和任务表，
 * 放在任何一个 feature 模块里都会让 feature 之间产生依赖（项目纪律禁止）。
 */

/** 侧边栏宽度；收起后只剩一条 48dp 的竖条。 */
private val SIDEBAR_WIDTH = 260.dp
private val SIDEBAR_COLLAPSED_WIDTH = 48.dp

enum class SidebarTab(val label: String) {
    CONVERSATIONS("会话"),
    TASKS("任务"),
}

/** 侧边栏的一行：id 用于选中态，title/subtitle 直接展示。 */
data class SidebarItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

@Composable
fun QuineSidebar(
    expanded: Boolean,
    tab: SidebarTab,
    conversations: List<SidebarItem>,
    tasks: List<SidebarItem>,
    selectedId: String?,
    onToggleExpanded: () -> Unit,
    onSelectTab: (SidebarTab) -> Unit,
    onSelectItem: (SidebarTab, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens
    val motion = QuineTheme.motion
    val reduced = QuineTheme.reducedMotion

    Row(modifier = modifier.fillMaxHeight()) {
        // 内容盒：不写死宽度，让 animateContentSize 跟 AnimatedVisibility 联动。
        // 写 .width(if) 会把宽度钉死，动画根本动不了 —— 那时收起后主内容区不会收回空间。
        Box(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = motion.duration(motion.standard, reduced),
                    ),
                )
                .widthIn(max = SIDEBAR_WIDTH)
                .fillMaxHeight()
                .background(colors.bg),
        ) {
            // 必须写全限定名：Row 作用域里还有一个 RowScope.AnimatedVisibility 变体，
            // 直接写 AnimatedVisibility 会解析到它上面去。
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(motion.duration(motion.micro, reduced))),
                exit = fadeOut(tween(motion.duration(motion.micro, reduced))),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SidebarTabRow(tab = tab, onSelect = onSelectTab)
                    com.quine.core.design.component.QuineDivider()
                    val items = when (tab) {
                        SidebarTab.CONVERSATIONS -> conversations
                        SidebarTab.TASKS -> tasks
                    }
                    if (items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (tab == SidebarTab.CONVERSATIONS) "暂无会话" else "暂无任务",
                                style = QuineTheme.typography.caption,
                                color = colors.textTertiary,
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(items, key = { "${tab.name}:${it.id}" }) { item ->
                                SidebarRow(
                                    item = item,
                                    selected = item.id == selectedId,
                                    onClick = { onSelectItem(tab, item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        // 竖条上的收起 / 展开把手：收起状态下也要能展开，否则是个死胡同。
        Box(
            modifier = Modifier
                .width(SIDEBAR_COLLAPSED_WIDTH)
                .fillMaxHeight()
                .clickable(onClickLabel = if (expanded) "收起侧边栏" else "展开侧边栏") {
                    onToggleExpanded()
                }
                .border(
                    width = dimens.hairline,
                    color = colors.line,
                    shape = RoundedCornerShape(0.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (expanded) "‹" else "›",
                style = QuineTheme.typography.footnote,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SidebarTabRow(tab: SidebarTab, onSelect: (SidebarTab) -> Unit) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.grid * 3, vertical = dimens.grid * 2),
        horizontalArrangement = Arrangement.spacedBy(dimens.grid * 2),
    ) {
        SidebarTab.entries.forEach { entry ->
            val selected = entry == tab
            Text(
                text = entry.label,
                style = QuineTheme.typography.footnote,
                color = if (selected) colors.onInk else colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radiusPill))
                    .background(if (selected) colors.ink else colors.fill)
                    .clickable(onClickLabel = entry.label) { onSelect(entry) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SidebarRow(
    item: SidebarItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = QuineTheme.colors
    val dimens = QuineTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.fill else colors.bg)
            .clickable(onClickLabel = item.title) { onClick() }
            .padding(horizontal = dimens.pageMargin, vertical = 10.dp),
    ) {
        Text(
            text = item.title,
            style = QuineTheme.typography.footnote,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.subtitle?.let {
            Text(
                text = it,
                style = QuineTheme.typography.caption,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
