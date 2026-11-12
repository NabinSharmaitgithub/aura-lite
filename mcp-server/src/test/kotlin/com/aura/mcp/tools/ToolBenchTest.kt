package com.aura.mcp.tools

import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.ToolResult
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tiny tool bench: runs read-only tools against a mock phone bridge and emits an HTML report.
 * The report lives under build/reports/ and is intentionally dependency-free.
 */
class ToolBenchTest {

    private class MockPhone : DeviceBridge {
        override suspend fun execute(tool: String, args: JsonObject): ToolResult = ToolResult.ok(
            changed = false,
            observed = buildJsonObject {
                put("tool", tool)
                put("mock", true)
            },
        )

        override suspend fun foregroundPackage(): String? = "com.example.mock"

        override suspend fun startWatching(events: List<String>) = Unit

        override suspend fun stopWatching() = Unit
    }

    @Test
    fun readOnlyToolsPassAgainstMockPhone() = runBlocking {
        val registry = McpToolRegistry(bridge = MockPhone())
        val cases = listOf(
            "get_device_status" to JsonObject(emptyMap()),
            "lookup_app" to buildJsonObject { put("query", "chrome") },
            "read_notifications" to JsonObject(emptyMap()),
        )
        val rows = cases.map { (tool, args) ->
            val result = registry.execute(tool, args)
            Triple(tool, result.ok, result.error?.code)
        }

        val reportDir = File("build/reports").apply { mkdirs() }
        val html = buildString {
            appendLine("<!doctype html><html><head><meta charset=\"utf-8\">")
            appendLine("<title>AURA tool bench</title>")
            appendLine("<style>body{font-family:monospace;padding:24px;background:#101010;color:#eee}")
            appendLine("table{border-collapse:collapse}td,th{border:1px solid #444;padding:6px 10px}")
            appendLine(".ok{color:#5f5}.bad{color:#f55}</style></head><body>")
            appendLine("<h1>AURA tool bench (mock phone)</h1><table>")
            appendLine("<tr><th>tool</th><th>ok</th><th>error</th></tr>")
            rows.forEach { (tool, ok, error) ->
                appendLine(
                    "<tr><td>$tool</td><td class=\"${if (ok) "ok" else "bad"}\">$ok</td>" +
                        "<td>${error ?: ""}</td></tr>",
                )
            }
            appendLine("</table></body></html>")
        }
        File(reportDir, "aura-tool-bench.html").writeText(html)

        assertTrue("all mock tool calls should pass: $rows", rows.all { it.second })
    }
}
