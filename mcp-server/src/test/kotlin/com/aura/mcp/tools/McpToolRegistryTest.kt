package com.aura.mcp.tools

import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.RestrictedAppsStore
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBridge : DeviceBridge {
    var executed = mutableListOf<String>()
    var foreground: String? = null

    override suspend fun execute(tool: String, args: JsonObject): ToolResult {
        executed += tool
        return ToolResult.ok(changed = true, observed = buildJsonObject { put("fake", 1) })
    }

    override suspend fun foregroundPackage(): String? = foreground

    override suspend fun startWatching(events: List<String>) = Unit
    override suspend fun stopWatching() = Unit
}

class FakeRestricted(private val blocked: Set<String>) : RestrictedAppsStore {
    override fun isBlocked(packageName: String): Boolean = blocked.contains(packageName)
    override fun blockedPackages(): Set<String> = blocked
}

class McpToolRegistryTest {

    @Test
    fun `unknown tool returns not_found without dispatching`() = runBlocking {
        val bridge = FakeBridge()
        val registry = McpToolRegistry(bridge)
        val result = registry.execute("bogus_tool")
        assertFalse(result.ok)
        assertEquals("not_found", result.error?.code)
        assertTrue(bridge.executed.isEmpty())
    }

    @Test
    fun `read_screen dispatches to the bridge`() = runBlocking {
        val bridge = FakeBridge()
        val registry = McpToolRegistry(bridge)
        val result = registry.execute("read_screen")
        assertTrue(result.ok)
        assertEquals(listOf("read_screen"), bridge.executed)
    }

    @Test
    fun `blocked foreground app refuses a mutating tool before dispatch`() = runBlocking {
        val bridge = FakeBridge().apply { foreground = "com.monzo.android" }
        val registry = McpToolRegistry(bridge, restrictedApps = FakeRestricted(setOf("com.monzo.android")))
        val result = registry.execute("compose_sms", buildJsonObject { })
        assertFalse(result.ok)
        assertEquals("blocked_app", result.error?.code)
        assertTrue("bridge must not be called", bridge.executed.isEmpty())
    }

    @Test
    fun `non-mutating tool is not blocked by foreground restriction`() = runBlocking {
        val bridge = FakeBridge().apply { foreground = "com.monzo.android" }
        val registry = McpToolRegistry(bridge, restrictedApps = FakeRestricted(setOf("com.monzo.android")))
        val result = registry.execute("read_screen")
        assertTrue(result.ok)
        assertEquals(listOf("read_screen"), bridge.executed)
    }

    @Test
    fun `risky tool without approval gate dispatches`() = runBlocking {
        val bridge = FakeBridge()
        val registry = McpToolRegistry(bridge)
        val result = registry.execute("compose_sms", buildJsonObject { })
        assertTrue(result.ok)
        assertEquals(listOf("compose_sms"), bridge.executed)
    }

    @Test
    fun `risky tool with denied gate does not dispatch`() = runBlocking {
        val bridge = FakeBridge()
        val registry = McpToolRegistry(bridge, approvalGate = { _ -> false })
        val result = registry.execute("compose_sms", buildJsonObject { })
        assertFalse(result.ok)
        assertEquals("approval_denied", result.error?.code)
        assertTrue(bridge.executed.isEmpty())
    }

    @Test
    fun `risky tool with approving gate dispatches`() = runBlocking {
        val bridge = FakeBridge()
        val registry = McpToolRegistry(bridge, approvalGate = { _ -> true })
        val result = registry.execute("compose_sms", buildJsonObject { })
        assertTrue(result.ok)
        assertEquals(listOf("compose_sms"), bridge.executed)
    }

    @Test
    fun `bridge crash is returned as an internal error`() = runBlocking {
        val bridge = object : DeviceBridge {
            override suspend fun execute(tool: String, args: JsonObject): ToolResult = error("boom")
            override suspend fun foregroundPackage(): String? = null
            override suspend fun startWatching(events: List<String>) = Unit
            override suspend fun stopWatching() = Unit
        }
        val registry = McpToolRegistry(bridge)
        val result = registry.execute("read_screen")
        assertFalse(result.ok)
        assertEquals(ToolError.INTERNAL, result.error?.code)
    }

    @Test
    fun `listTools returns every catalog tool`() {
        val registry = McpToolRegistry(FakeBridge())
        assertEquals(ToolCatalog.all.size, registry.tools.size)
        assertEquals(ToolCatalog.toolListJson().size, registry.listTools().size)
    }

    @Test
    fun `withBridge preserves restriction behaviour on the new bridge`() = runBlocking {
        val first = FakeBridge()
        val registry = McpToolRegistry(first, restrictedApps = FakeRestricted(setOf("blocked")))
        val second = FakeBridge().apply { foreground = "blocked" }
        val swapped = registry.withBridge(second)
        val result = swapped.execute("compose_sms")
        assertFalse(result.ok)
        assertEquals("blocked_app", result.error?.code)
        assertTrue(second.executed.isEmpty())
    }
}
