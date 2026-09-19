package com.quine.core.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationDaoTest {

    private lateinit var database: QuineDatabase
    private lateinit var store: ConversationStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, QuineDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ConversationStore(database.conversationDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun 会话与消息往返() = runBlocking {
        val conversation = store.ensureConversation(preferredId = null)

        store.append(conversation.id, com.quine.core.gateway.ChatRole.USER, "帮我看看 a.txt")
        store.append(conversation.id, com.quine.core.gateway.ChatRole.ASSISTANT, "读到了")

        val messages = store.observeMessages(conversation.id).first()
        assertEquals(2, messages.size)
        assertEquals("帮我看看 a.txt", messages[0].text)
        assertEquals("读到了", messages[1].text)
    }

    @Test
    fun 首条用户消息顺手定标题() = runBlocking {
        val conversation = store.ensureConversation(preferredId = null)
        store.append(conversation.id, com.quine.core.gateway.ChatRole.USER, "帮我把 notes/todo.md 里没做完的整理一下")

        val reloaded = store.observeConversations().first().single()
        assertTrue(reloaded.title.startsWith("帮我把 notes/todo.md"))
        assertTrue(reloaded.title.length <= 21)
    }

    @Test
    fun 工具调用与元数据不丢() = runBlocking {
        val conversation = store.ensureConversation(preferredId = null)
        store.append(
            conversationId = conversation.id,
            role = com.quine.core.gateway.ChatRole.TOOL,
            text = "[私有工作区] a.txt — 共 5 字节\n\nhello",
            toolCallId = "call_1",
            toolName = "fs_read",
            meta = MessageMeta(ok = true, sourceRef = "私有工作区:a.txt#bytes:0-5", truncated = false),
        )

        val message = store.observeMessages(conversation.id).first().single()
        assertEquals("call_1", message.toolCallId)
        assertEquals("fs_read", message.toolName)
        assertEquals("私有工作区:a.txt#bytes:0-5", message.meta.sourceRef)
    }

    @Test
    fun 历史可以直接喂给loop() = runBlocking {
        val conversation = store.ensureConversation(preferredId = null)
        store.append(conversation.id, com.quine.core.gateway.ChatRole.USER, "你好")
        store.append(conversation.id, com.quine.core.gateway.ChatRole.ASSISTANT, "你好，我是 Quine。")

        val history = store.history(conversation.id)
        assertEquals(2, history.size)
        assertEquals(com.quine.core.gateway.ChatRole.USER, history[0].role)
        assertEquals("你好", history[0].content)
    }

    @Test
    fun 删会话会级联删消息() = runBlocking {
        val conversation = store.ensureConversation(preferredId = null)
        store.append(conversation.id, com.quine.core.gateway.ChatRole.USER, "hi")

        store.deleteConversation(conversation.id)

        assertEquals(0, store.messageCount(conversation.id))
        assertTrue(store.observeConversations().first().isEmpty())
    }

    @Test
    fun 冷启动恢复上次会话() = runBlocking {
        val first = store.ensureConversation(preferredId = null)
        val second = store.ensureConversation(preferredId = null)
        assertEquals(first.id, second.id)
    }

    @Test
    fun 指定的会话不存在时回落最新会话() = runBlocking {
        val existing = store.ensureConversation(preferredId = null)
        val resolved = store.ensureConversation(preferredId = "不存在的 id")
        assertEquals(existing.id, resolved.id)
    }

    @Test
    fun 空会话查询不报错() = runBlocking {
        assertNull(database.conversationDao().findConversation("nope"))
        assertEquals(0, store.messageCount("nope"))
    }
}
