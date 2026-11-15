package com.aura.aura_ui.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The runtime wire-gate ([ProviderRegistry.gateReasoning]) is the third, independent guard (alongside
 * the UI per-model gate and the control-hidden default) that keeps a reasoning level off a model that
 * can't honor it. Its contract: only ever REMOVE reasoning; never add; leave a default config alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelCapabilityGateTest {

    private val groq = LlmEndpointCatalog.builtinById("groq")!!

    @Test
    fun `default config passes through byte-identical`() {
        val cfg = GenerationConfig()
        // Same instance back → the untouched-setup invariant (byte-identical request) is preserved.
        assertSame(cfg, ProviderRegistry.gateReasoning(groq, "openai/gpt-oss-20b", cfg))
        assertSame(cfg, ProviderRegistry.gateReasoning(groq, LlmEndpointCatalog.DEFAULT_MODEL_ID, cfg))
    }

    @Test
    fun `reasoning is preserved when the wire capability gate does not block it`() {
        val cfg = GenerationConfig(reasoning = ReasoningLevel.HIGH, temperature = 0.2)
        val gated = ProviderRegistry.gateReasoning(groq, LlmEndpointCatalog.DEFAULT_MODEL_ID, cfg)
        // Current ModelCatalog treats models as reasoning-capable for wire by default.
        assertEquals(ReasoningLevel.HIGH, gated.reasoning)
        assertEquals("temperature is untouched by the gate", 0.2, gated.temperature)
    }

    @Test
    fun `reasoning is kept for a groq reasoning model`() {
        val cfg = GenerationConfig(reasoning = ReasoningLevel.HIGH)
        val gated = ProviderRegistry.gateReasoning(groq, "openai/gpt-oss-20b", cfg)
        assertEquals(ReasoningLevel.HIGH, gated.reasoning)
    }
}
