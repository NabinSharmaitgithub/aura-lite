package com.aura.aura_ui.agent.llm

/**
 * The provider catalogue. 14 built-ins plus a custom OpenAI-compatible endpoint whose profile
 * is stored as JSON in preferences.
 *
 * Capability flags are *inferences*, not assertions: the fetched model catalogue overrides them
 * where available.
 */
object LlmEndpointCatalog {

    private fun openAiProfile(extra: Map<String, String> = emptyMap(), reasoning: String? = "reasoning_effort") =
        ProviderProfile(reasoningParam = reasoning, extraHeaders = extra)

    val builtIns: List<LlmEndpoint> = listOf(
        LlmEndpoint(
            id = "groq",
            displayName = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_MODEL_ID,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(),
            isBuiltIn = true,
            keyHint = "gsk_…",
            keyUrl = "https://console.groq.com/keys",
            defaultModelId = "llama-3.3-70b-versatile",
        ),
        LlmEndpoint(
            id = "openrouter",
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_MODEL_ID,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(
                extra = mapOf("HTTP-Referer" to "https://aura.local", "X-Title" to "AURA"),
            ),
            isBuiltIn = true,
            keyHint = "sk-or-…",
            keyUrl = "https://openrouter.ai/keys",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "gemini",
            displayName = "Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_PROVIDER,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(reasoning = "reasoning_effort"),
            isBuiltIn = true,
            keyHint = "AIza…",
            keyUrl = "https://aistudio.google.com/apikey",
            defaultModelId = "gemini-2.0-flash",
        ),
        LlmEndpoint(
            id = "openai",
            displayName = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_PROVIDER,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(),
            isBuiltIn = true,
            keyHint = "sk-…",
            keyUrl = "https://platform.openai.com/api-keys",
            defaultModelId = "gpt-4o-mini",
        ),
        LlmEndpoint(
            id = "anthropic",
            displayName = "Anthropic",
            baseUrl = "https://api.anthropic.com/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.ANTHROPIC_X_API_KEY,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_PROVIDER,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = ProviderProfile(
                reasoningParam = "thinking",
                visionContentShape = "anthropic",
                extraHeaders = mapOf("anthropic-version" to "2023-06-01"),
            ),
            isBuiltIn = true,
            keyHint = "sk-ant-…",
            keyUrl = "https://console.anthropic.com/settings/keys",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "mistral",
            displayName = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_MODEL_ID,
            reasoningInference = ReasoningInference.NONE,
            profile = openAiProfile(reasoning = null),
            isBuiltIn = true,
            keyHint = "API key",
            keyUrl = "https://console.mistral.ai/api-keys",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "dashscope",
            displayName = "Qwen / DashScope",
            baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_MODEL_ID,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(reasoning = null),
            isBuiltIn = true,
            keyHint = "sk-…",
            keyUrl = "https://bailian.console.aliyun.com/",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "cerebras",
            displayName = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.NEVER,
            reasoningInference = ReasoningInference.NONE,
            profile = openAiProfile(reasoning = null),
            isBuiltIn = true,
            keyHint = "csk-…",
            keyUrl = "https://cloud.cerebras.ai/",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "zai",
            displayName = "Z.ai (GLM)",
            baseUrl = "https://api.z.ai/api/paas/v4",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_MODEL_ID,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(reasoning = "thinking"),
            isBuiltIn = true,
            keyHint = "API key",
            keyUrl = "https://z.ai/manage-apikey/apikey-list",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "xai",
            displayName = "xAI",
            baseUrl = "https://api.x.ai/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_PROVIDER,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(reasoning = "reasoning_effort"),
            isBuiltIn = true,
            keyHint = "xai-…",
            keyUrl = "https://console.x.ai/",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "together",
            displayName = "Together",
            baseUrl = "https://api.together.xyz/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_MODEL_ID,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(),
            isBuiltIn = true,
            keyHint = "API key",
            keyUrl = "https://api.together.ai/settings/api-keys",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "moonshot",
            displayName = "Moonshot",
            baseUrl = "https://api.moonshot.ai/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_MODEL_ID,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(reasoning = null),
            isBuiltIn = true,
            keyHint = "sk-…",
            keyUrl = "https://platform.moonshot.cn/console/api-keys",
            defaultModelId = null,
        ),
        LlmEndpoint(
            id = "deepseek",
            displayName = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.NEVER,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(reasoning = null),
            isBuiltIn = true,
            keyHint = "sk-…",
            keyUrl = "https://platform.deepseek.com/api_keys",
            defaultModelId = "deepseek-chat",
        ),
        LlmEndpoint(
            id = "nvidia",
            displayName = "NVIDIA (NIM)",
            baseUrl = "https://integrate.api.nvidia.com/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.BY_MODEL_ID,
            reasoningInference = ReasoningInference.BY_MODEL_ID,
            profile = openAiProfile(reasoning = null),
            isBuiltIn = true,
            keyHint = "nvapi-…",
            keyUrl = "https://build.nvidia.com/",
            defaultModelId = "meta/llama-3.3-70b-instruct",
        ),
    )

    val custom: LlmEndpoint
        get() = LlmEndpoint(
            id = "custom",
            displayName = "Custom endpoint",
            baseUrl = "https://example.com/v1",
            chatCompletionsPath = "/chat/completions",
            modelsUrl = "/models",
            authScheme = AuthScheme.BEARER,
            modelListStyle = ModelListStyle.OPENAI_DATA,
            visionInference = VisionInference.NEVER,
            reasoningInference = ReasoningInference.NONE,
            profile = ProviderProfile(),
            isBuiltIn = false,
            keyHint = "API key",
            keyUrl = null,
            defaultModelId = null,
        )

    val all: List<LlmEndpoint> get() = builtIns + custom

    fun byId(id: String): LlmEndpoint = all.firstOrNull { it.id == id } ?: builtIns.first()

    fun fromCustomJson(raw: String): LlmEndpoint? {
        if (raw.isBlank()) return null
        return runCatching {
            val o = LlmJson.json.parseToJsonElement(raw) as kotlinx.serialization.json.JsonObject
            fun str(key: String, fallback: String = "") = (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: fallback
            custom.copy(
                id = str("id", "custom"),
                displayName = str("displayName", "Custom endpoint"),
                baseUrl = str("baseUrl").trimEnd('/'),
                chatCompletionsPath = str("chatCompletionsPath", "/chat/completions"),
                modelsUrl = str("modelsUrl", "/models"),
                keyHint = str("keyHint", "API key"),
                defaultModelId = str("defaultModelId").ifBlank { null },
            )
        }.getOrNull()
    }

    /** Capability inference from the model id, used only until the catalogue is fetched. */
    fun supportsVision(endpoint: LlmEndpoint, modelId: String): Boolean {
        val lower = modelId.lowercase()
        return when (endpoint.visionInference) {
            VisionInference.ALWAYS, VisionInference.BY_PROVIDER -> true
            VisionInference.NEVER -> false
            VisionInference.BY_MODEL_ID ->
                listOf("vision", "4o", "llava", "pixtral", "qwen-vl", "gemini", "claude-3", "claude-4", "llama-3.2-11b-vision", "llama-3.2-90b-vision", "minicpm", "internvl")
                    .any { lower.contains(it) }
        }
    }

    fun supportsReasoning(endpoint: LlmEndpoint, modelId: String): Boolean {
        val lower = modelId.lowercase()
        return when (endpoint.reasoningInference) {
            ReasoningInference.BY_PROVIDER -> true
            ReasoningInference.NONE -> false
            ReasoningInference.BY_MODEL_ID ->
                listOf("reason", "r1", "thinking", "qwq", "o1", "o3", "o4", "glm-4.5", "magistral")
                    .any { lower.contains(it) }
        }
    }
}
