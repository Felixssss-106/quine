package com.quine.core.tools.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGuardTest {

    @Test
    fun `接受常规相对路径`() {
        assertEquals("notes/todo.md", ok("notes/todo.md"))
        assertEquals("a/b/c.txt", ok("a/b/c.txt"))
    }

    @Test
    fun `去掉首尾空白并容忍重复分隔符`() {
        assertEquals("notes/todo.md", ok("  notes/todo.md  "))
        assertEquals("a/b", ok("a//b"))
    }

    @Test
    fun `拒绝空路径`() {
        assertRejected("")
        assertRejected("   ")
        assertRejected("///")
    }

    @Test
    fun `拒绝绝对路径`() {
        assertRejected("/etc/passwd")
        assertRejected("\\Windows\\system32")
    }

    @Test
    fun `拒绝反斜杠`() {
        assertRejected("notes\\todo.md")
    }

    @Test
    fun `拒绝跳转段`() {
        assertRejected("..")
        assertRejected("../secret.txt")
        assertRejected("notes/../../secret.txt")
        assertRejected("./notes/todo.md")
        assertRejected("notes/./todo.md")
    }

    @Test
    fun `拒绝控制字符`() {
        assertRejected("notes/todo\u0000.md")
    }

    @Test
    fun `拒绝时给出人话三段`() {
        val rejected = PathGuard.normalize("../secret.txt") as PathCheck.Rejected
        assertTrue(rejected.error.message.isNotEmpty())
        assertTrue(rejected.error.impact.isNotEmpty())
        assertTrue(rejected.error.nextStep.isNotEmpty())
    }

    private fun ok(raw: String): String {
        val result = PathGuard.normalize(raw)
        assertTrue("期望通过，实际被拒：$raw", result is PathCheck.Ok)
        return (result as PathCheck.Ok).relativePath
    }

    private fun assertRejected(raw: String) {
        assertTrue("期望被拒，实际通过：$raw", PathGuard.normalize(raw) is PathCheck.Rejected)
    }
}
