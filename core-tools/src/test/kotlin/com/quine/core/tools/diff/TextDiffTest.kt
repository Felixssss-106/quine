package com.quine.core.tools.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {

    @Test
    fun 内容相同则全部未变() {
        val lines = TextDiff.diff("a\nb\nc", "a\nb\nc")
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.kind == DiffKind.UNCHANGED })
        assertFalse(TextDiff.summary(lines).changed)
    }

    @Test
    fun 纯新增() {
        val lines = TextDiff.diff("a\nb", "a\nb\nc")
        val summary = TextDiff.summary(lines)
        assertEquals(1, summary.added)
        assertEquals(0, summary.removed)
        assertEquals(2, summary.unchanged)

        val added = lines.single { it.kind == DiffKind.ADDED }
        assertEquals("c", added.text)
        assertEquals(3, added.newNumber)
        assertEquals(null, added.oldNumber)
    }

    @Test
    fun 纯删除() {
        val lines = TextDiff.diff("a\nb\nc", "a\nc")
        val summary = TextDiff.summary(lines)
        assertEquals(0, summary.added)
        assertEquals(1, summary.removed)
        assertEquals(2, summary.unchanged)

        val removed = lines.single { it.kind == DiffKind.REMOVED }
        assertEquals("b", removed.text)
        assertEquals(2, removed.oldNumber)
        assertEquals(null, removed.newNumber)
    }

    @Test
    fun 中间改一行是删加配对且顺序正确() {
        val lines = TextDiff.diff("one\ntwo\nthree", "one\nTWO\nthree")

        // 反序遍历后要还原成正向顺序
        assertEquals(listOf("one", "two", "TWO", "three"), lines.map { it.text })
        assertEquals(
            listOf(DiffKind.UNCHANGED, DiffKind.REMOVED, DiffKind.ADDED, DiffKind.UNCHANGED),
            lines.map { it.kind },
        )
    }

    @Test
    fun 行号随位置正确递增() {
        val lines = TextDiff.diff("a\nb\nc\nd", "a\nX\nc\nd")
        val changed = lines.single { it.kind == DiffKind.ADDED }
        assertEquals(2, changed.newNumber)
        val removed = lines.single { it.kind == DiffKind.REMOVED }
        assertEquals(2, removed.oldNumber)
    }

    @Test
    fun 渲染用符号表达增删_颜色之外还有保底通道() {
        val rendered = TextDiff.render(TextDiff.diff("a\nb", "a\nc"))
        assertEquals(" a\n-b\n+c\n", rendered)
    }

    @Test
    fun 空输入不炸() {
        assertTrue(TextDiff.diff("", "").isEmpty())
        val fromEmpty = TextDiff.diff("", "a")
        assertEquals(1, TextDiff.summary(fromEmpty).added)
    }

    @Test
    fun 超大文件退化为整体替换而不是卡死() {
        val oldText = (1..3_000).joinToString("\n") { "old-$it" }
        val newText = (1..3_000).joinToString("\n") { "new-$it" }

        val lines = TextDiff.diff(oldText, newText)
        val summary = TextDiff.summary(lines)
        assertEquals(3_000, summary.removed)
        assertEquals(3_000, summary.added)
        assertEquals(0, summary.unchanged)
    }
}
