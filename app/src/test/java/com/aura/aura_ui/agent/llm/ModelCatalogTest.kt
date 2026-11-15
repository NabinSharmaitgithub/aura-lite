package com.aura.aura_ui.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {

    @Test
    fun `authHeaders returns bearer header for bearer endpoints`() {
        val groq = LlmEndpointCatalog.builtinById(LlmEndpointCatalog.GROQ_ID)!!
        val headers = groq.authHeaders("test-key")
        assertEquals("Bearer test-key", headers["Authorization"])
    }

    @Test
    fun `authHeaders returns x-goog-api-key for gemini`() {
        val gemini = LlmEndpointCatalog.builtinById(LlmEndpointCatalog.GEMINI_ID)!!
        val headers = gemini.authHeaders("AIza-test")
        assertEquals("AIza-test", headers["x-goog-api-key"])
        assertNull(headers["Authorization"])
    }

    @Test
    fun `authHeaders returns x-api-key for anthropic`() {
        val anthropic = LlmEndpointCatalog.builtinById("anthropic")!!
        val headers = anthropic.authHeaders("sk-ant-test")
        assertEquals("sk-ant-test", headers["x-api-key"])
        assertNull(headers["Authorization"])
    }

    @Test
    fun `authHeaders omits key when blank`() {
        val groq = LlmEndpointCatalog.builtinById(LlmEndpointCatalog.GROQ_ID)!!
        assertTrue(groq.authHeaders("").isEmpty() || groq.authHeaders("")["Authorization"] == null)
    }

    @Test
    fun `inferVision detects common vision markers`() {
        assertTrue(ModelCatalog.inferVision("gpt-4o"))
        assertTrue(ModelCatalog.inferVision("gemini-1.5-flash"))
        assertTrue(ModelCatalog.inferVision("qwen-vl-max"))
        assertFalse(ModelCatalog.inferVision("text-bison"))
    }

    @Test
    fun `inferReasoning detects reasoning models`() {
        assertTrue(ModelCatalog.inferReasoning("o1-preview"))
        assertTrue(ModelCatalog.inferReasoning("deepseek-r1"))
        assertFalse(ModelCatalog.inferReasoning("gpt-4o"))
    }

    @Test
    fun `inferCheap detects lightweight models`() {
        assertTrue(ModelCatalog.inferCheap("gpt-4o-mini"))
        assertTrue(ModelCatalog.inferCheap("gemini-1.5-flash"))
        assertFalse(ModelCatalog.inferCheap("gpt-4o"))
    }

    @Test
    fun `reasoningCapableForWire defaults to true`() {
        val groq = LlmEndpointCatalog.builtinById(LlmEndpointCatalog.GROQ_ID)!!
        assertTrue(ModelCatalog.reasoningCapableForWire(groq, "meta-llama/llama-4-scout-17b-16e-instruct"))
    }

    @Test
    fun `parse openai model list extracts ids and context`() {
        val groq = LlmEndpointCatalog.builtinById(LlmEndpointCatalog.GROQ_ID)!!
        val body = """{"data":[{"id":"llama-3.3-70b-versatile","context_window":131072}]}"""
        val models = ModelCatalog.parse(groq, body)
        assertEquals(1, models.size)
        assertEquals("llama-3.3-70b-versatile", models[0].id)
        assertEquals(131072, models[0].contextWindow)
    }

    @Test
    fun `parse gemini model list extracts models`() {
        val gemini = LlmEndpointCatalog.builtinById(LlmEndpointCatalog.GEMINI_ID)!!
        val body = """{"models":[{"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","inputTokenLimit":1048576}]}"""
        val models = ModelCatalog.parse(gemini, body)
        assertEquals(1, models.size)
        assertEquals("gemini-2.5-flash", models[0].id)
        assertEquals("Gemini 2.5 Flash", models[0].displayName)
        assertEquals(1048576, models[0].contextWindow)
    }
}
