package com.aura.mcp.guard

import com.aura.mcp.bridge.ToolDefinition
import com.aura.mcp.bridge.ToolDomain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTests {

    private fun def(mutating: Boolean) = ToolDefinition(
        name = "tap",
        description = "test",
        inputSchema = kotlinx.serialization.json.JsonObject(emptyMap()),
        domain = ToolDomain.UI,
        mutating = mutating,
    )

    @Test
    fun foregroundGuardBlocksSensitivePackageForMutation() {
        val decision = ForegroundGuard.evaluate(
            foregroundPackage = "com.google.android.packageinstaller",
            tool = "tap",
            definition = def(mutating = true),
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun foregroundGuardAllowsBenignPackageForMutation() {
        val decision = ForegroundGuard.evaluate(
            foregroundPackage = "com.example.notes",
            tool = "tap",
            definition = def(mutating = true),
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun foregroundGuardDoesNotBlockReadOnlyToolsInSystemPackage() {
        val decision = ForegroundGuard.evaluate(
            foregroundPackage = "com.android.systemui",
            tool = "tap",
            definition = def(mutating = false),
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun otpSightingDetectsCodeInJsonPayload() {
        val payload = """{"text":"Your verification code is 123456. Do not share it."}"""
        val sight = OtpSighting.inspect(payload)
        assertTrue(sight.detected)
    }

    @Test
    fun otpSightingIgnoresPlainTextWithoutKeyword() {
        val sight = OtpSighting.inspect("""{"text":"Order 123456 has shipped"}""")
        assertFalse(sight.detected)
    }

    @Test
    fun otpSightingOnlyGuardsPropagationTools() {
        assertTrue(OtpSighting.blocksTool("type_text"))
        assertTrue(OtpSighting.blocksTool("set_value"))
        assertFalse(OtpSighting.blocksTool("read_screen"))
    }
}
