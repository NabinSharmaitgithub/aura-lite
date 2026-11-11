package com.aura.aura_ui.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.agent.llm.LlmEndpoint
import com.aura.aura_ui.data.skills.Skill
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.navigation.Routes
import com.aura.aura_ui.presentation.openUrl
import com.aura.aura_ui.ui.components.AuraSwitch
import com.aura.aura_ui.ui.components.AuraTextField
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.EmptyState
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.GroupedList
import com.aura.aura_ui.ui.components.GroupedRow
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.KeyValueRow
import com.aura.aura_ui.ui.components.LoadState
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.SectionHeader
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall
import androidx.compose.ui.platform.LocalContext
import com.aura.mcp.bridge.ToolDomain

@Composable
fun AgentHubScreen(vm: AuraViewModel, onNavigate: (String) -> Unit) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val endpoint = vm.currentEndpoint()
    val keySet = vm.hasKey(endpoint)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Agent", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "The parts that let AURA think, act and remember.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            GroupedList(header = "Intelligence") {
                GroupedRow(
                    title = "Brain",
                    subtitle = "${endpoint.displayName} · ${if (keySet) "key saved" else "no key"}",
                    icon = Icons.Outlined.Psychology,
                    onClick = { onNavigate(Routes.AGENT_BRAIN) },
                )
                GroupedRow(
                    title = "Tools",
                    subtitle = "${vm.toolCount} tools on one contract",
                    icon = Icons.Outlined.Build,
                    onClick = { onNavigate(Routes.AGENT_TOOLS) },
                )
                GroupedRow(
                    title = "Hooks",
                    subtitle = "Deterministic rules around every run",
                    icon = Icons.Outlined.Bolt,
                    onClick = { onNavigate(Routes.AGENT_HOOKS) },
                    isLast = true,
                )
            }
            Spacer(Modifier.height(16.dp))
            GroupedList(header = "Memory & procedure") {
                GroupedRow(
                    title = "Skills",
                    subtitle = "Reusable procedures the planner may follow",
                    icon = Icons.Outlined.Tune,
                    onClick = { onNavigate(Routes.AGENT_SKILLS) },
                )
                GroupedRow(
                    title = "Memory",
                    subtitle = "Learnings, reminders and corrections",
                    icon = Icons.Outlined.Memory,
                    onClick = { onNavigate(Routes.AGENT_MEMORY) },
                )
                GroupedRow(
                    title = "Companion",
                    subtitle = "Voice companion and overlay behaviour",
                    icon = Icons.Outlined.RecordVoiceOver,
                    onClick = { onNavigate(Routes.AGENT_COMPANION) },
                    isLast = true,
                )
            }
            Spacer(Modifier.height(16.dp))
            GroupedList(header = "Voice") {
                GroupedRow(
                    title = "Voice settings",
                    subtitle = if (settings.wakeWordEnabled) "Wake word is on" else "Wake word is off",
                    icon = Icons.Outlined.SmartToy,
                    onClick = { onNavigate(Routes.AGENT_VOICE) },
                    isLast = true,
                )
            }
            Spacer(Modifier.height(20.dp))
            SurfaceCard {
                Text("Local only", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "AURA has no account and no cloud. The only network calls are the provider you choose, an optional Tavily key, and the local MCP client.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}

@Composable
fun ProviderSettingsScreen(vm: AuraViewModel, onBack: () -> Unit) {
    val mono = LocalMonoScheme.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val verifying by vm.modelsLoading.collectAsStateWithLifecycle()
    val verification by vm.verification.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val selected = vm.currentEndpoint()

    var keyInput by remember { mutableStateOf("") }
    var modelQuery by remember { mutableStateOf("") }
    var customJson by remember(settings.customEndpointJson) { mutableStateOf(settings.customEndpointJson) }
    var promptOverride by remember(settings.systemPromptOverride) { mutableStateOf(settings.systemPromptOverride) }

    LaunchedEffect(selected.id) {
        keyInput = ""
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Brain", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose one provider, store its key locally, and pick a model.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionHeader("Provider")
            GroupedList {
                vm.endpoints.forEachIndexed { index, endpoint ->
                    EndpointRow(
                        endpoint = endpoint,
                        selected = endpoint.id == settings.llmEndpointId,
                        hasKey = vm.hasKey(endpoint),
                        onClick = { vm.selectEndpoint(endpoint.id) },
                        isLast = index == vm.endpoints.lastIndex,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Key, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(selected.displayName, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Badge(if (vm.hasKey(selected)) "saved" else "missing", tone = if (vm.hasKey(selected)) ChipTone.Live else ChipTone.Broken)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Hint: ${selected.keyHint}",
                    style = MonoStyleSmall,
                    color = mono.textSecondary,
                )
                vm.keyHint(selected)?.let {
                    Text("Stored: $it", style = MonoStyleSmall, color = mono.textSecondary)
                }
                selected.keyUrl?.let { url ->
                    GhostButton("Get a key ↗", { context.openUrl(url) })
                }
                Spacer(Modifier.height(10.dp))
                AuraTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = if (vm.hasKey(selected)) "Replace key…" else "Paste ${selected.keyHint}",
                    textStyle = MonoStyle,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton(
                        label = "Save",
                        onClick = {
                            vm.saveProviderKey(selected, keyInput)
                            keyInput = ""
                        },
                        modifier = Modifier.weight(1f),
                        enabled = keyInput.isNotBlank(),
                    )
                    SecondaryButton(
                        label = if (verifying) "Checking…" else "Verify",
                        onClick = { vm.verifyProviderKey(selected, keyInput) },
                        modifier = Modifier.weight(1f),
                        enabled = !verifying && (keyInput.isNotBlank() || vm.hasKey(selected)),
                    )
                }
                verification?.let { result ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        result.message,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = if (result.ok) mono.successDot else mono.blood,
                    )
                    if (result.modelCount > 0) {
                        Text("${result.modelCount} models available", style = MonoStyleSmall, color = mono.textSecondary)
                    }
                }
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (settings.llmEndpointId == "custom") {
            item {
                SectionHeader("Custom endpoint JSON")
                SurfaceCard {
                    AuraTextField(
                        value = customJson,
                        onValueChange = { customJson = it },
                        placeholder = """{"id":"custom","baseUrl":"https://example.com/v1","chatCompletionsPath":"/chat/completions","modelsUrl":"/models","defaultModelId":"model-id"}""",
                        singleLine = false,
                        minHeight = 160.dp,
                        textStyle = MonoStyle,
                    )
                    Spacer(Modifier.height(10.dp))
                    InkButton("Save endpoint", { vm.saveCustomEndpoint(customJson) }, enabled = customJson.isNotBlank())
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Models", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = mono.textPrimary, modifier = Modifier.weight(1f))
                GhostButton(if (verifying) "Loading…" else "Refresh", { vm.fetchModels() }, color = mono.textPrimary)
            }
            Spacer(Modifier.height(8.dp))
            AuraTextField(
                value = modelQuery,
                onValueChange = { modelQuery = it },
                placeholder = "Search models",
                trailing = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = mono.textSecondary, modifier = Modifier.size(18.dp))
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (verifying && models.isEmpty()) {
            item { LoadState("Loading the live model catalogue…") }
        } else if (models.isEmpty()) {
            item {
                EmptyState(
                    title = "No catalogue loaded",
                    detail = "Save a key and tap Verify. AURA reads the provider's live model list.",
                )
            }
        } else {
            val filtered = models.filter {
                modelQuery.isBlank() || it.id.contains(modelQuery, ignoreCase = true) || it.displayName.contains(modelQuery, ignoreCase = true)
            }
            items(filtered.take(80), key = { it.id }) { model ->
                val selectedModel = model.id == settings.llmModelId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selectedModel) mono.surface else mono.canvas)
                        .clickable { vm.selectModel(model.id) }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(model.id, style = MonoStyle, color = mono.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (model.displayName != model.id || model.contextWindow != null) {
                            Text(
                                listOfNotNull(
                                    model.displayName.takeIf { it != model.id },
                                    model.contextWindow?.let { "${it / 1000}k ctx" },
                                ).joinToString(" · "),
                                style = MonoStyleSmall,
                                color = mono.textSecondary,
                            )
                        }
                    }
                    model.badges.forEach { badge -> Badge(badge) ; Spacer(Modifier.width(4.dp)) }
                    if (selectedModel) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(mono.ink))
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            SectionHeader("Behaviour")
            SurfaceCard {
                KeyValueRow("Temperature", "%.1f".format(settings.temperature))
                Slider(
                    value = settings.temperature,
                    onValueChange = { vm.setTemperature(it) },
                    valueRange = 0f..2f,
                    steps = 19,
                )
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Max steps", "${settings.maxSteps}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("-4", { vm.setMaxSteps(settings.maxSteps - 4) }, Modifier.weight(1f))
                    SecondaryButton("+4", { vm.setMaxSteps(settings.maxSteps + 4) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionHeader("System prompt override")
            SurfaceCard {
                AuraTextField(
                    value = promptOverride,
                    onValueChange = { promptOverride = it },
                    placeholder = "Leave blank to use the built-in prompt",
                    singleLine = false,
                    minHeight = 140.dp,
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Save prompt", { vm.saveSystemPromptOverride(promptOverride) })
            }
        }
    }
}

@Composable
private fun EndpointRow(
    endpoint: LlmEndpoint,
    selected: Boolean,
    hasKey: Boolean,
    onClick: () -> Unit,
    isLast: Boolean,
) {
    val mono = LocalMonoScheme.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(18.dp).clip(CircleShape).background(if (selected) mono.ink else mono.outline),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(7.dp).clip(CircleShape).background(mono.textOnInk))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(endpoint.displayName, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Text(
                    endpoint.baseUrl.removePrefix("https://"),
                    style = MonoStyleSmall,
                    color = mono.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Badge(if (hasKey) "key" else "—", tone = if (hasKey) ChipTone.Live else ChipTone.Idle)
        }
        if (!isLast) {
            Box(Modifier.padding(start = 46.dp, end = 16.dp).fillMaxWidth().height(1.dp).background(mono.outline))
        }
    }
}

@Composable
fun ToolsScreen(vm: AuraViewModel, onBack: () -> Unit) {
    val mono = LocalMonoScheme.current
    var query by remember { mutableStateOf("") }
    val tools = vm.tools.filter {
        query.isBlank() || it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
    }
    val grouped = tools.groupBy { it.domain }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Tools", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "${vm.toolCount} tools. The in-app agent and every trusted MCP client use this exact list.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            AuraTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search name or description",
                trailing = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = mono.textSecondary, modifier = Modifier.size(18.dp))
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        if (tools.isEmpty()) {
            item { EmptyState("No tool matches", "Try a shorter search.") }
        }

        grouped.forEach { (domain, list) ->
            item {
                SectionHeader("${domain.label} · ${list.size}")
            }
            items(list, key = { it.name }) { tool ->
                SurfaceCard(
                    modifier = Modifier.padding(vertical = 4.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tool.name, style = MonoStyle, color = mono.textPrimary, modifier = Modifier.weight(1f))
                        if (tool.mutating) Badge("mutating", tone = ChipTone.Idle)
                        if (tool.risky) {
                            Spacer(Modifier.width(4.dp))
                            Badge("risky", tone = ChipTone.Broken)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tool.description,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        com.aura.mcp.tools.ToolCatalog.signature(tool.name),
                        style = MonoStyleSmall,
                        color = mono.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun HooksScreen(vm: AuraViewModel, onBack: () -> Unit) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var instructions by remember(settings.hookInstructions) { mutableStateOf(settings.hookInstructions) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Hooks", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Hooks are deterministic. They run the same way every time, before or after the planner.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            SectionHeader("Always on")
            GroupedList {
                HookRow("Destructive-goal gate", "Payments, sends and deletes stop for an explicit confirmation card.", true, isFirst = true)
                HookRow("Verify after mutation", "Every mutating tool is followed by a screen observation.", true)
                HookRow("Loop guard", "An identical call with no state change ends the run.", true)
                HookRow("Step budget", "The run stops at ${settings.maxSteps} steps, never silently.", true)
                HookRow("Restricted apps", "AURA refuses to act in blocked packages and returns blocked_app.", true, isLast = true)
            }
            Spacer(Modifier.height(20.dp))
            SectionHeader("Before-run instructions")
            SurfaceCard {
                Text(
                    "Appended to the system prompt for every run. Use it for house rules, not for secrets.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                AuraTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    placeholder = "e.g. Prefer WhatsApp over SMS. Never open banking apps.",
                    singleLine = false,
                    minHeight = 130.dp,
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                InkButton("Save hook", {
                    vm.updateSettings { it.hookInstructions = instructions.trim() }
                    vm.showNotice("Run hooks saved")
                })
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            SurfaceCard {
                Text("Hooks cannot grant permissions", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "A hook can add instructions and guardrails. It cannot bypass a missing permission, a restricted app, or a confirmation.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun HookRow(title: String, detail: String, on: Boolean, isFirst: Boolean = false, isLast: Boolean = false) {
    val mono = LocalMonoScheme.current
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
            }
            Badge(if (on) "on" else "off", tone = if (on) ChipTone.Live else ChipTone.Idle)
        }
        if (!isLast) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(mono.outline))
    }
}

@Composable
fun SkillsScreen(vm: AuraViewModel, onBack: () -> Unit) {
    val mono = LocalMonoScheme.current
    val skills by vm.skillList.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var raw by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Skills", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Procedures the planner may follow. Skills are prompt-side, never executable code.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
        }

        if (skills.isEmpty()) {
            item { EmptyState("No skills yet", "Built-in packs are seeded on first launch.") }
        }

        items(skills, key = { it.id }) { skill ->
            SkillCard(
                skill = skill,
                onToggle = { vm.setSkillEnabled(skill, it) },
                onDelete = if (skill.builtIn) null else ({ vm.deleteSkill(skill) }),
            )
            Spacer(Modifier.height(10.dp))
        }

        item {
            Spacer(Modifier.height(10.dp))
            SectionHeader("Import a skill pack")
            SurfaceCard {
                Text(
                    "Paste JSON with id, name, trigger, steps, tools and guardrails.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                AuraTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    placeholder = """{"id":"my_skill","name":"My skill","trigger":"when…","steps":["…"]}""",
                    singleLine = false,
                    minHeight = 140.dp,
                    textStyle = MonoStyleSmall,
                )
                Spacer(Modifier.height(10.dp))
                InkButton("Import", {
                    vm.importSkill(raw)
                    raw = ""
                }, enabled = raw.isNotBlank())
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun SkillCard(skill: Skill, onToggle: (Boolean) -> Unit, onDelete: (() -> Unit)?) {
    val mono = LocalMonoScheme.current
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(skill.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Text(
                    (if (skill.builtIn) "Built-in" else "User pack") + " · v${skill.version}",
                    style = MonoStyleSmall,
                    color = mono.textSecondary,
                )
            }
            AuraSwitch(checked = skill.enabled, onCheckedChange = onToggle)
        }
        if (skill.trigger.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(skill.trigger, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
        }
        if (skill.steps.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(skill.steps.joinToString(" → "), style = MonoStyleSmall, color = mono.textSecondary)
        }
        if (skill.tools.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                skill.tools.take(6).forEach { Badge(it) }
            }
        }
        if (onDelete != null) {
            Spacer(Modifier.height(8.dp))
            GhostButton("Delete", onDelete, color = mono.blood)
        }
    }
}
