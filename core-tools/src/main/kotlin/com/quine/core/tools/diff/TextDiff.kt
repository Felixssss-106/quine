package com.quine.core.tools.diff

/**
 * 行级文本 diff（page-specs.md §2 的 diff 块）。
 *
 * 关于配色：**这里不带任何颜色**。设计文档要求新增用「+」+ 极浅 ink 底、
 * 删除用「−」+ 灰字删除线，**不用红绿**（黑白纪律）—— 那是 UI 层的决定，
 * diff 只负责给出 [DiffKind] 与行号，`+/−` 前缀是无障碍的保底通道。
 */
enum class DiffKind {
    UNCHANGED,
    ADDED,
    REMOVED,
}

data class DiffLine(
    val kind: DiffKind,
    val text: String,
    /** 旧文件中的行号（1 起）；新增行为 null。 */
    val oldNumber: Int?,
    /** 新文件中的行号（1 起）；删除行为 null。 */
    val newNumber: Int?,
)

data class DiffSummary(
    val added: Int,
    val removed: Int,
    val unchanged: Int,
) {
    val changed: Boolean get() = added > 0 || removed > 0
}

object TextDiff {

    /**
     * 经典 LCS 动态规划。输入通常是几百行以内的文本文件；超过 [MAX_LINES] 时退化为
     * 「整体替换」，避免 O(n·m) 的时间和内存把界面卡死 —— 那种规模本来也不该看 diff。
     */
    fun diff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = oldText.splitLines()
        val newLines = newText.splitLines()

        if (oldLines.isEmpty() && newLines.isEmpty()) return emptyList()
        if (oldLines.size > MAX_LINES || newLines.size > MAX_LINES) return wholesale(oldLines, newLines)
        if (oldLines == newLines) return oldLines.mapIndexed { i, line -> DiffLine(DiffKind.UNCHANGED, line, i + 1, i + 1) }

        val lcs = lcsTable(oldLines, newLines)

        val result = ArrayList<DiffLine>(oldLines.size + newLines.size)
        var i = oldLines.size
        var j = newLines.size
        while (i > 0 && j > 0) {
            when {
                oldLines[i - 1] == newLines[j - 1] -> {
                    result += DiffLine(DiffKind.UNCHANGED, oldLines[i - 1], i, j)
                    i--
                    j--
                }

                // 平局时先走 ADDED：回溯是从尾往头收集的，最后会反转，
                // 这样最终顺序才是「先删除后新增」（与 git 的 -/+ 惯例一致）。
                lcs[i - 1][j] > lcs[i][j - 1] -> {
                    result += DiffLine(DiffKind.REMOVED, oldLines[i - 1], i, null)
                    i--
                }

                else -> {
                    result += DiffLine(DiffKind.ADDED, newLines[j - 1], null, j)
                    j--
                }
            }
        }
        while (i > 0) {
            result += DiffLine(DiffKind.REMOVED, oldLines[i - 1], i, null)
            i--
        }
        while (j > 0) {
            result += DiffLine(DiffKind.ADDED, newLines[j - 1], null, j)
            j--
        }
        return result.asReversed()
    }

    fun summary(lines: List<DiffLine>): DiffSummary = DiffSummary(
        added = lines.count { it.kind == DiffKind.ADDED },
        removed = lines.count { it.kind == DiffKind.REMOVED },
        unchanged = lines.count { it.kind == DiffKind.UNCHANGED },
    )

    /**
     * 渲染成带 `+` / `−` / 空格前缀的文本。前缀是**冗余通道**：即使颜色看不出来
     * （色弱、黑白模式、终端块里），也能靠符号分辨增删。
     */
    fun render(lines: List<DiffLine>): String = buildString {
        for (line in lines) {
            when (line.kind) {
                DiffKind.ADDED -> append('+')
                DiffKind.REMOVED -> append('-')
                DiffKind.UNCHANGED -> append(' ')
            }
            append(line.text)
            append('\n')
        }
    }

    private fun wholesale(oldLines: List<String>, newLines: List<String>): List<DiffLine> = buildList {
        oldLines.forEachIndexed { i, line -> add(DiffLine(DiffKind.REMOVED, line, i + 1, null)) }
        newLines.forEachIndexed { i, line -> add(DiffLine(DiffKind.ADDED, line, null, i + 1)) }
    }

    private fun lcsTable(a: List<String>, b: List<String>): Array<IntArray> {
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices) {
            for (j in b.indices) {
                table[i + 1][j + 1] = if (a[i] == b[j]) {
                    table[i][j] + 1
                } else {
                    maxOf(table[i][j + 1], table[i + 1][j])
                }
            }
        }
        return table
    }

    private fun String.splitLines(): List<String> = if (isEmpty()) emptyList() else split('\n')

    private const val MAX_LINES = 2_000
}
