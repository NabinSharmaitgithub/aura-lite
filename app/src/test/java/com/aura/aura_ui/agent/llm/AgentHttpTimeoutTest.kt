package com.aura.aura_ui.agent.llm

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The agent must give up on a stalled LLM request.
 *
 * ### The failure this pins (device capture 2026-08-05 20:42)
 *
 * ```
 * 20:42:54.873  ▶ Running goal: open amazon.com in a web browser
 * 20:42:55.147  → POST .../chat/completions (body 63771 chars)
 *               (silence — minutes)
 * ```
 *
 * One request, no response, no error, no retry, forever. `buildHttpClient` installed
 * interceptors and no timeouts at all, so a half-open socket — the ordinary result of a
 * phone changing network mid-request — parked the whole run indefinitely and the UI sat
 * on "working on it".
 *
 * `RetryingLLMClient` could not save it either: it retries *errors* (429/5xx/overloaded),
 * and a hang never produces one. The safety net only catches failures that actually fail.
 *
 * So the requirement is not "be fast" — it is **fail loudly and in bounded time**, because
 * only a failure can reach the retry ladder or an honest message to the user.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentHttpTimeoutTest {

    /**
     * Well under [AgentHttpTimeouts.REQUEST_MS] so the test stays fast, but far longer than a
     * healthy localhost round trip, so passing cannot be an accident of speed.
     */
    private val testBudgetMs = 4_000L

    // The JUnit timeout is the regression guard: if the client's budgets are ever removed
    // again, this fails in 30 s instead of hanging the build the way it hung the phone.
    @Test(timeout = 30_000)
    fun `a request that never gets a response fails instead of hanging`() {
        val server = ServerSocket(0)
        val port = server.localPort
        // Accept the connection, then stall so client timeouts must fire.
        thread(isDaemon = true) {
            runCatching {
                val socket = server.accept()
                Thread.sleep(testBudgetMs + 15_000)
                runCatching { socket.close() }
            }
        }

        try {
            val client = OpenAiCompatProvider.buildHttpClient(
                profile = ProviderProfile(),
                generation = GenerationConfig(),
                timeouts = AgentHttpTimeouts(requestMs = testBudgetMs, connectMs = testBudgetMs, socketMs = testBudgetMs),
            )
            val startedAt = System.nanoTime()
            try {
                runBlocking {
                    client.post("http://127.0.0.1:$port/v1/chat/completions") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"model":"test","messages":[]}""")
                    }
                }
                fail("expected the stalled request to fail, but it returned a response")
            } catch (expected: Exception) {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                assertTrue(
                    "must fail within the configured budget, not OkHttp's default; took ${elapsedMs}ms",
                    elapsedMs < testBudgetMs * 3,
                )
            }
        } finally {
            runCatching { server.close() }
        }
    }

    @Test
    fun `a healthy response is not disturbed by the timeouts`() {
        val server = ServerSocket(0)
        val port = server.localPort
        thread(isDaemon = true) {
            runCatching {
                val socket = server.accept()
                socket.soTimeout = 2000
                val input = socket.getInputStream()
                val buf = ByteArray(4096)
                // Best-effort consume request headers/body so client can proceed.
                runCatching { input.read(buf) }
                val body = """{"ok":true}"""
                val response =
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                socket.getOutputStream().write(response.toByteArray())
                socket.getOutputStream().flush()
                socket.close()
            }
        }

        try {
            val client = OpenAiCompatProvider.buildHttpClient(
                profile = ProviderProfile(),
                generation = GenerationConfig(),
            )
            runBlocking {
                val resp = client.post("http://127.0.0.1:$port/v1/chat/completions") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"model":"test","messages":[]}""")
                }
                assertTrue("healthy call must succeed", resp.status.value == 200)
            }
        } finally {
            runCatching { server.close() }
        }
    }

    @Test
    fun `the shipped budget is generous enough for a real vision turn`() {
        // A vision turn with thinking legitimately runs tens of seconds; the timeout
        // exists to catch DEAD connections, not slow ones. Too tight and it would abort
        // work that was about to succeed — the opposite failure, and a worse one.
        assertTrue(
            "request budget must clear a slow-but-real turn",
            AgentHttpTimeouts().requestMs >= 90_000,
        )
        assertTrue(
            "connect budget must be short — a dead host should not cost a minute",
            AgentHttpTimeouts().connectMs <= 20_000,
        )
    }
}
