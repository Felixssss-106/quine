package com.quine.core.gateway

import com.quine.core.common.ErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiCompatProviderTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun provider(
        baseUrl: String = server.url("/v1").toString(),
        model: String = "deepseek-chat",
        retryPolicy: RetryPolicy = RetryPolicy(baseDelayMillis = 5),
        apiKey: String? = "sk-test-abcdefghijklmnop",
    ) = OpenAiCompatProvider(
        baseUrl = baseUrl,
        model = model,
        apiKeyProvider = { apiKey },
        dispatcher = Dispatchers.IO,
        retryPolicy = retryPolicy,
    )

    private fun request(messages: List<LlmMessage> = listOf(LlmMessage(ChatRole.USER, "你好"))) =
        LlmRequest(messages = messages)

    private val sseBody = buildString {
        append("data: {\"choices\":[{\"delta\":{\"content\":\"你\"},\"finish_reason\":null}]}\n\n")
        append("data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n")
        append("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2,\"total_tokens\":7}}\n\n")
        append("data: [DONE]\n\n")
    }

    @Test
    fun `streams text deltas and usage`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val events = provider().stream(request()).toList()

        assertEquals(listOf("你", "好"), events.filterIsInstance<LlmEvent.TextDelta>().map { it.text })
        assertEquals(LlmEvent.Usage(5, 2, 7), events.filterIsInstance<LlmEvent.Usage>().single())
        assertTrue(events.any { it is LlmEvent.Finish })

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test-abcdefghijklmnop", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"stream\":true"))
    }

    @Test
    fun `stream assembles tool calls across chunks`() = runTest {
        val body = buildString {
            append("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"function\":{\"name\":\"fs_read\",\"arguments\":\"{\\\"path\\\":\"}}]}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"notes.md\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))

        val assembler = ToolCallAssembler()
        provider().stream(request()).toList().forEach { event ->
            if (event is LlmEvent.ToolCallDelta) assembler.accept(event)
        }

        val call = assembler.build().single()
        assertEquals("fs_read", call.name)
        assertEquals("""{"path":"notes.md"}""", call.argumentsJson)
    }

    @Test
    fun `maps unauthorized response to auth error`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid api key","type":"auth_error"}}"""),
        )

        val error = captureFailure { provider().stream(request()).toList() }

        assertNotNull(error)
        assertEquals(ErrorKind.AUTH, error!!.error.kind)
    }

    @Test
    fun `retries transient server error before first delta`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("upstream down"))
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(sseBody))

        val events = provider().stream(request()).toList()

        assertEquals(2, server.requestCount)
        assertTrue(events.filterIsInstance<LlmEvent.TextDelta>().isNotEmpty())
    }

    @Test
    fun `does not retry after content already streamed`() = runTest {
        val broken = "data: {\"choices\":[{\"delta\":{\"content\":\"半\"}}]}\n\ndata: {not-json}\n\n"
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(broken))

        val error = captureFailure { provider().stream(request()).toList() }

        assertEquals(1, server.requestCount)
        assertNotNull(error)
    }

    @Test
    fun `falls back to full json when server ignores stream flag`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"完整回复"}}]}"""),
        )

        val events = provider().stream(request()).toList()

        assertEquals("完整回复", events.filterIsInstance<LlmEvent.TextDelta>().single().text)
    }

    @Test
    fun `fails fast without api key`() = runTest {
        val error = captureFailure { provider(apiKey = null).stream(request()).toList() }
        assertNotNull(error)
        assertEquals(ErrorKind.AUTH, error!!.error.kind)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `checkKey succeeds against models endpoint`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))

        val result = provider().checkKey()

        assertTrue(result.isSuccess)
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun `checkKey probes chat endpoint when models missing`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"content":"pong"}}]}"""))

        val result = provider().checkKey()

        assertTrue(result.isSuccess)
        assertEquals("/v1/models", server.takeRequest().path)
        assertEquals("/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun `checkKey keeps auth failures visible`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))

        val result = provider().checkKey()

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as LlmException
        assertEquals(ErrorKind.AUTH, error.error.kind)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): LlmException? = try {
        block()
        null
    } catch (error: LlmException) {
        error
    }
}
