package com.aura.aura_ui.presentation.screens

import android.Manifest
import android.app.Activity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.data.learnings.LearningFact
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.formatDuration
import com.aura.aura_ui.presentation.navigation.Routes
import com.aura.aura_ui.presentation.openIntent
import com.aura.aura_ui.presentation.openPackageSettings
import com.aura.aura_ui.presentation.openUrl
import com.aura.aura_ui.presentation.rememberPermissions
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
import com.aura.aura_ui.ui.components.PermissionBanner
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.SectionHeader
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MonoSettingsScreen(vm: AuraViewModel, onNavigate: (String) -> Unit) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val blocked by vm.restrictedPackages.collectAsStateWithLifecycle()
    var name by remember(settings.userName) { mutableStateOf(settings.userName) }
    val permissions = rememberPermissions(vm)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Settings", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(16.dp))
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(mono.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = mono.textOnInk, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (settings.userName.isBlank()) "Your profile" else settings.userName, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                        Text("Local profile · no account", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                    }
                    AuraSwitch(checked = settings.darkMode, onCheckedChange = { vm.updateSettings { s -> s.darkMode = it } })
                }
                Spacer(Modifier.height(12.dp))
                AuraTextField(value = name, onValueChange = { name = it }, placeholder = "Name AURA should use")
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Save name", {
                    vm.updateSettings { it.userName = name.trim() }
                    vm.showNotice("Name saved")
                })
            }
            Spacer(Modifier.height(18.dp))
            GroupedList(header = "Agent") {
                GroupedRow(title = "Brain", subtitle = vm.currentEndpoint().displayName, icon = Icons.Outlined.Psychology, onClick = { onNavigate(Routes.AGENT_BRAIN) })
                GroupedRow(title = "Voice", subtitle = if (settings.wakeWordEnabled) "Wake word on" else "Wake word off", icon = Icons.Outlined.RecordVoiceOver, onClick = { onNavigate(Routes.AGENT_VOICE) }, isLast = true)
            }
            Spacer(Modifier.height(16.dp))
            GroupedList(header = "Control") {
                GroupedRow(
                    title = "Permissions health",
                    subtitle = if (permissions.accessibility) "Accessibility is on" else "Action needed",
                    icon = Icons.Outlined.Shield,
                    onClick = { onNavigate(Routes.SETTINGS_PERMISSIONS) },
                )
                GroupedRow(title = "Restricted apps", subtitle = "${blocked.size} blocked", icon = Icons.Outlined.Apps, onClick = { onNavigate(Routes.SETTINGS_RESTRICTED_APPS) })
                GroupedRow(title = "Trusted devices", subtitle = "MCP clients on your LAN", icon = Icons.Outlined.Devices, onClick = { onNavigate(Routes.SETTINGS_TRUSTED_DEVICES) })
                GroupedRow(title = "Search key", subtitle = "Tavily · optional", icon = Icons.Outlined.Search, onClick = { onNavigate(Routes.SETTINGS_TAVILY) }, isLast = true)
            }
            Spacer(Modifier.height(16.dp))
            GroupedList(header = "Cloud — optional, off by default") {
                GroupedRow(
                    title = "Firebase",
                    subtitle = if (vm.firebaseAvailable) "Account sync & diagnostics" else "Not configured on this build",
                    icon = Icons.Outlined.Cloud,
                    onClick = { onNavigate(Routes.SETTINGS_FIREBASE) },
                )
                GroupedRow(
                    title = "Backblaze B2",
                    subtitle = "Private bucket for agent uploads & exports",
                    icon = Icons.Outlined.CloudUpload,
                    onClick = { onNavigate(Routes.SETTINGS_B2) },
                    isLast = true,
                )
            }
            Spacer(Modifier.height(16.dp))
            GroupedList(header = "Legal") {
                GroupedRow(title = "Privacy", subtitle = "No account, no telemetry", icon = Icons.Outlined.Lock, onClick = { onNavigate(Routes.SETTINGS_PRIVACY) })
                GroupedRow(title = "Terms", subtitle = "The short version", icon = Icons.Outlined.Description, onClick = { onNavigate(Routes.SETTINGS_TERMS) })
                GroupedRow(title = "Licences", subtitle = "Open-source notices", icon = Icons.Outlined.Article, onClick = { onNavigate(Routes.SETTINGS_LICENSES) }, isLast = true)
            }
            Spacer(Modifier.height(16.dp))
            GroupedList(header = "Developer") {
                GroupedRow(
                    title = "Developer mode",
                    subtitle = "Exposes the eval suite and retention tools",
                    icon = Icons.Outlined.Settings,
                    trailing = { AuraSwitch(checked = settings.developerModeEnabled, onCheckedChange = { vm.updateSettings { s -> s.developerModeEnabled = it } }) },
                    showChevron = false,
                    isLast = !settings.developerModeEnabled,
                )
                if (settings.developerModeEnabled) {
                    GroupedRow(title = "Eval suite", subtitle = "20-task quality harness", icon = Icons.Outlined.Article, onClick = { onNavigate(Routes.EVAL_SUITE) })
                    GroupedRow(title = "Log retention", subtitle = "${settings.logRetentionDays} days", icon = Icons.Outlined.Settings, onClick = { onNavigate(Routes.ACTIVITY_RETENTION) }, isLast = true)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("AURA ${vm.packageVersion()} · local build", style = MonoStyleSmall, color = mono.textSecondary)
        }
    }
}

@Composable
fun TavilyKeyScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val context = LocalContext.current
    val state by vm.tavilyState.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 32.dp),
    ) {
        Text("Search key", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tavily is optional. Without it, web_search fails with a fix path instead of pretending.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = mono.textSecondary,
        )
        Spacer(Modifier.height(20.dp))
        SurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tavily API key", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Spacer(Modifier.weight(1f))
                Badge(if (vm.tavilyKeyHint() != null) "saved" else "missing", tone = if (vm.tavilyKeyHint() != null) ChipTone.Live else ChipTone.Broken)
            }
            vm.tavilyKeyHint()?.let {
                Spacer(Modifier.height(4.dp))
                Text("Stored: $it", style = MonoStyleSmall, color = mono.textSecondary)
            }
            Spacer(Modifier.height(8.dp))
            GhostButton("Get a key ↗", { context.openUrl("https://app.tavily.com/home") })
            Spacer(Modifier.height(10.dp))
            AuraTextField(value = key, onValueChange = { key = it }, placeholder = "tvly-…", textStyle = MonoStyle)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkButton("Save", {
                    vm.saveTavilyKey(key)
                    key = ""
                }, modifier = Modifier.weight(1f), enabled = key.isNotBlank())
                SecondaryButton("Remove", { vm.saveTavilyKey("") }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Verify with a test search", { vm.verifyTavily() })
            state?.let {
                Spacer(Modifier.height(10.dp))
                Text(it.message, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = if (it.ok) mono.successDot else mono.blood)
            }
            notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        SurfaceCard {
            Text(
                "The key lives in EncryptedSharedPreferences. It is sent only to api.tavily.com, never to AURA or a third party.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = mono.textSecondary,
            )
        }
    }
}

@Composable
fun TrustedDevicesScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val context = LocalContext.current
    val health by vm.mcpHealth.collectAsStateWithLifecycle()
    val trusted by vm.trustedDevices.collectAsStateWithLifecycle()
    val audit by vm.auditLines.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var port by remember(settings.mcpPort) { mutableStateOf(settings.mcpPort.toString()) }
    var payload by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(Unit) { vm.refreshTrusted(); vm.refreshAudit() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Trusted devices", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "The local MCP server lets a paired client use the same tool contract as the in-app agent.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MCP server", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                        Text(
                            if (health.running) "Listening on ${health.host}:${health.port}" else "Stopped",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = mono.textSecondary,
                        )
                    }
                    AuraSwitch(
                        checked = settings.mcpEnabled,
                        onCheckedChange = { if (it) vm.startMcpServer() else vm.stopMcpServer() },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AuraTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        placeholder = "Port",
                        modifier = Modifier.weight(1f),
                        textStyle = MonoStyle,
                    )
                    Spacer(Modifier.width(10.dp))
                    SecondaryButton("Apply", {
                        port.toIntOrNull()?.let { vm.setMcpPort(it) }
                    })
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Tools", "${health.toolCount}")
                KeyValueRow("Paired clients", "${health.clientCount}")
                KeyValueRow("Uptime", health.uptimeMs.formatDuration())
                health.lastError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.blood)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton("Pair a device", {
                        val result = vm.beginPairing()
                        if (result == null) {
                            vm.showNotice("Start the server first")
                        } else {
                            payload = result.toQrString()
                            qr = runCatching {
                                val matrix = QRCodeWriter().encode(result.toQrString(), BarcodeFormat.QR_CODE, 512, 512)
                                Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                                    for (x in 0 until matrix.width) {
                                        for (y in 0 until matrix.height) {
                                            bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                        }
                                    }
                                }
                            }.getOrNull()
                        }
                    }, modifier = Modifier.weight(1f))
                    SecondaryButton("Refresh", { vm.refreshTrusted() }, modifier = Modifier.weight(1f))
                }
                payload?.let { raw ->
                    Spacer(Modifier.height(12.dp))
                    qr?.let { bitmap ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Pairing QR code",
                                modifier = Modifier.size(200.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        raw,
                        style = MonoStyleSmall,
                        color = mono.textSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    SecondaryButton("Copy pairing payload", {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AURA pairing", raw))
                        vm.showNotice("Pairing payload copied")
                    })
                }
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionHeader("Paired clients · ${trusted.size}")
        }

        if (trusted.isEmpty()) {
            item { EmptyState("No paired clients", "Pairing issues a bearer token; only its hash is stored.") }
        } else {
            items(trusted, key = { it.clientId }) { device ->
                SurfaceCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(device.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                            Text(
                                "${device.clientId} · seen ${vm.relativeTime(device.lastSeenAt)}",
                                style = MonoStyleSmall,
                                color = mono.textSecondary,
                            )
                            Text(
                                "scopes: ${device.scopes.joinToString(", ")}",
                                style = MonoStyleSmall,
                                color = mono.textSecondary,
                            )
                        }
                        GhostButton("Revoke", { vm.revokeDevice(device.clientId) }, color = mono.blood)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Audit", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = mono.textPrimary, modifier = Modifier.weight(1f))
                GhostButton("Refresh", { vm.refreshAudit() })
            }
            Spacer(Modifier.height(8.dp))
            if (audit.isEmpty()) {
                SurfaceCard {
                    Text("No MCP calls yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            } else {
                SurfaceCard {
                    audit.takeLast(30).reversed().forEach { line ->
                        Text(
                            line.take(300),
                            style = MonoStyleSmall,
                            color = mono.textSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("LAN only, TLS, bearer token", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "The server binds 0.0.0.0 only while enabled. Pairing nonces expire after two minutes. Risky calls still require a notification approval.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}

@Composable
fun RestrictedAppsScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val blocked by vm.restrictedPackages.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<com.aura.aura_ui.automation.AppEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { vm.installedApps() }
        loaded = true
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Restricted apps", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "AURA refuses every tool while a blocked package is foreground. The refusal is a normal blocked_app result.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            AuraTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search installed apps",
                trailing = { Icon(Icons.Outlined.Search, contentDescription = null, tint = mono.textSecondary, modifier = Modifier.size(18.dp)) },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge("${blocked.size} blocked", tone = if (blocked.isEmpty()) ChipTone.Idle else ChipTone.Broken)
                Spacer(Modifier.weight(1f))
                if (blocked.isNotEmpty()) GhostButton("Clear all", { blocked.forEach { vm.setRestricted(it, false) } }, color = mono.blood)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!loaded) {
            item { com.aura.aura_ui.ui.components.LoadState("Reading installed apps…") }
        } else if (filtered.isEmpty()) {
            item { EmptyState("No apps match", "Try a package name.") }
        } else {
            items(filtered, key = { it.packageName }) { app ->
                SurfaceCard(modifier = Modifier.padding(vertical = 3.dp), contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MonoStyleSmall, color = mono.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        AuraSwitch(
                            checked = app.packageName in blocked,
                            onCheckedChange = { vm.setRestricted(app.packageName, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsHealthScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val context = LocalContext.current
    val permissions = rememberPermissions(vm)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Permissions", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "AURA checks live state every second. Nothing here is inferred from a cached boolean.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            SectionHeader("Critical")
            GroupedList {
                PermissionRow("Accessibility service", "Read and act on the screen", permissions.accessibility, "Open settings", {
                    context.openIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                }, isFirst = true)
                PermissionRow("Provider key", "The model that plans and answers", vm.hasKey(vm.currentEndpoint()), "Open Brain", {
                    vm.showNotice("Use Agent → Brain to add a key")
                })
                PermissionRow("Screen capture", "MediaProjection consent for vision and receipts", permissions.screenCapture, "Grant", {
                    vm.requestScreenCapture()
                }, isLast = true)
            }
            Spacer(Modifier.height(18.dp))
            SectionHeader("Helpful")
            GroupedList {
                PermissionRow("Microphone", "Voice input and wake word", permissions.microphone, "Grant", {
                    launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }, isFirst = true)
                PermissionRow("Notifications", "Run status and approvals", permissions.notifications, "Grant", {
                    if (Build.VERSION.SDK_INT >= 33) launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                })
                PermissionRow("Display over other apps", "Companion pill and edge glow", permissions.overlay, "Open settings", {
                    context.openPackageSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                })
                PermissionRow("Notification listener", "Read and dismiss notifications", permissions.notificationListener, "Open settings", {
                    context.openIntent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                })
                PermissionRow("Contacts", "Resolve names without guessing", permissions.contacts, "Grant", {
                    launcher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                })
                PermissionRow("Battery unrestricted", "Keeps long runs alive", permissions.batteryUnrestricted, "Open settings", {
                    context.openPackageSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                })
                PermissionRow("Exact alarms", "Reminders fire on time", permissions.exactAlarms, "Open settings", {
                    if (Build.VERSION.SDK_INT >= 31) {
                        context.openPackageSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    }
                }, isLast = true)
            }
            Spacer(Modifier.height(18.dp))
            if (!permissions.accessibility) {
                PermissionBanner(
                    text = "Without accessibility, every UI tool returns permission_denied. This is the one permission AURA truly needs.",
                    action = "Fix",
                    onAction = { context.openIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                )
                Spacer(Modifier.height(12.dp))
            }
            SurfaceCard {
                Text(
                    "Android 13+ blocks sideloaded apps from enabling accessibility until you allow restricted settings for AURA in App info.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val mono = LocalMonoScheme.current
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
            }
            if (granted) {
                Badge("granted", tone = ChipTone.Live)
            } else {
                GhostButton(action, onAction, color = mono.blood)
            }
        }
        if (!isLast) {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(mono.outline))
        }
    }
}

@Composable
fun PrivacyPolicyScreen(vm: AuraViewModel, onResetOnboarding: () -> Unit) {
    val mono = LocalMonoScheme.current
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 32.dp),
    ) {
        Text("Privacy", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
        Spacer(Modifier.height(10.dp))
        LegalBody(LegalCopy.privacy)
        Spacer(Modifier.height(22.dp))
        SurfaceCard {
            Text("Your controls", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Delete learnings in Agent → Memory. Delete traces in Activity. Remove provider keys in Brain. Reset onboarding below.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            SecondaryButton("Reset onboarding", { confirmReset = true })
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset onboarding?") },
            text = { Text("This only resets the wizard. Settings, keys and memory stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetOnboarding()
                    onResetOnboarding()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun TermsScreen() {
    LegalPage("Terms", LegalCopy.terms)
}

@Composable
fun LicensesScreen() {
    LegalPage("Licences", LegalCopy.licenses)
}

@Composable
private fun LegalPage(title: String, body: String) {
    val mono = LocalMonoScheme.current
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 32.dp),
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
        Spacer(Modifier.height(10.dp))
        LegalBody(body)
    }
}

@Composable
private fun LegalBody(text: String) {
    val mono = LocalMonoScheme.current
    Text(
        text,
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        color = mono.textSecondary,
    )
}

object LegalCopy {
    val privacy = """
        AURA is a local Android agent. There is no account, no sign-in, no cloud sync, no advertising ID and no analytics or crash-reporting SDK in this build.

        What stays on the device
        • Provider keys and the optional Tavily key live in EncryptedSharedPreferences.
        • Session traces, screenshots, learnings, reminders, skills, contacts cache and the MCP audit log live in app-private storage.
        • Logs are pruned on the retention window you choose (7 days by default).

        What leaves the device
        • Only the request you initiate: the goal, recent trace context and any screenshots you enabled, sent to the model provider you configured.
        • A Tavily search query, if you saved a Tavily key and asked for a web search.
        • Nothing else. There is no AURA server.

        Your controls
        • Turn off learnings, screenshot attachment, diagnostics, wake word and MCP independently.
        • Delete individual facts, reminders, skills and runs; clear all session logs; reset onboarding.
        • Revoke a paired MCP client at any time. Only a hash of its bearer token is stored.

        Children and sensitive data
        AURA never enters passwords, payment details or government identifiers. It asks you to do that yourself. Do not use AURA to automate banking or other restricted apps; block them in Restricted apps.
    """.trimIndent()

    val terms = """
        AURA is provided as-is for personal automation on your own device. You are responsible for the goals you submit and for the apps you allow it to touch.

        1. Bring your own model key. Provider usage, cost and terms are between you and that provider.
        2. Automation can fail. AURA verifies changes and stops on repeated failure, but it cannot guarantee that a third-party app behaved as expected.
        3. Destructive goals require a confirmation card. Do not attempt to bypass it.
        4. Do not use AURA to violate another service's terms, to access accounts that are not yours, or to perform unlawful actions.
        5. The software is supplied without warranty. Back up anything important before automating it.
        6. Third-party components are covered by their own licences; see Licences.

        By continuing you accept these terms and the privacy statement.
    """.trimIndent()

    val licenses = """
        AURA is built with open-source software. Principal components:

        • Kotlin, Kotlin Coroutines, kotlinx.serialization — Apache 2.0
        • AndroidX, Jetpack Compose, Material 3, Room, WorkManager, Lifecycle, Navigation — Apache 2.0
        • Hilt / Dagger — Apache 2.0
        • OkHttp, Retrofit — Apache 2.0
        • Ktor — Apache 2.0
        • Koog — Apache 2.0
        • Model Context Protocol Kotlin SDK — Apache 2.0
        • ONNX Runtime — MIT
        • ML Kit Text Recognition — Google ML Kit terms
        • sherpa-onnx — Apache 2.0
        • Silero VAD — MIT
        • Bouncy Castle — MIT-style
        • ZXing — Apache 2.0
        • Apache Commons Codec — Apache 2.0
        • Lottie for Compose — Apache 2.0

        Full licence texts are available from each project's repository. This screen is a notice, not a replacement for those licences.
    """.trimIndent()
}

@Composable
fun FirebaseScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val busy by vm.firebaseBusy.collectAsStateWithLifecycle()
    val email by vm.firebaseEmail.collectAsStateWithLifecycle()
    val message by vm.firebaseMessage.collectAsStateWithLifecycle()
    var fieldEmail by remember(email) { mutableStateOf(email ?: "") }
    var password by remember { mutableStateOf("") }

    // Google sign-in is offered only when this build carries google-services.json, which
    // generates the default_web_client_id string. Otherwise the button stays hidden and
    // email/password still works.
    val activity = context as? Activity
    val googleClient = remember(activity) {
        val webClientId = runCatching {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (id != 0) context.getString(id) else null
        }.getOrNull()
        val act = activity
        if (act != null && webClientId != null) {
            GoogleSignIn.getClient(
                act,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build(),
            )
        } else {
            null
        }
    }
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        task.addOnSuccessListener { account ->
            val idToken = account?.idToken
            if (idToken != null) vm.firebaseSignInWithGoogle(idToken) else vm.showNotice("Google sign-in returned no token")
        }
        task.addOnFailureListener { vm.showNotice("Google sign-in cancelled or failed") }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Firebase", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Optional cloud account. Off by default — AURA is fully offline until you link one.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))

            if (!vm.firebaseAvailable) {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = mono.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Not configured on this build",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = mono.textPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This APK was compiled without app/google-services.json, so nothing leaves the device. " +
                            "To enable sign-in, sync and push, create a Firebase project, download its google-services.json " +
                            "into app/ and rebuild (or set the GOOGLE_SERVICES_JSON_BASE64 secret for CI). See firebase/README.md.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                }
                return@item
            }

            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cloud account", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Badge(
                        if (email != null) "linked" else "off",
                        tone = if (email != null) ChipTone.Live else ChipTone.Broken,
                    )
                }
                if (email != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Signed in as $email", style = MonoStyleSmall, color = mono.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Sign out", { vm.firebaseSignOut() })
                } else {
                    Spacer(Modifier.height(8.dp))
                    AuraTextField(value = fieldEmail, onValueChange = { fieldEmail = it }, placeholder = "you@example.com", textStyle = MonoStyle)
                    Spacer(Modifier.height(8.dp))
                    AuraTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Password (6+ characters)",
                        textStyle = MonoStyle,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InkButton(
                            "Create account",
                            { vm.firebaseCreateAccount(fieldEmail, password) },
                            modifier = Modifier.weight(1f),
                            enabled = fieldEmail.isNotBlank() && password.length >= 6 && !busy,
                        )
                        SecondaryButton(
                            "Sign in",
                            { vm.firebaseSignIn(fieldEmail, password) },
                            modifier = Modifier.weight(1f),
                            enabled = fieldEmail.isNotBlank() && password.isNotBlank() && !busy,
                        )
                    }
                    googleClient?.let { client ->
                        Spacer(Modifier.height(10.dp))
                        GhostButton("Continue with Google", { googleLauncher.launch(client.signInIntent) })
                    }
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        message,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Sync") || message.contains("ready")) mono.successDot else mono.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            GroupedList(header = "Sync — last write wins") {
                GroupedRow(
                    title = "Sync now",
                    subtitle = "Facts, reminders/plans and skills; sessions only if enabled below",
                    icon = Icons.Outlined.CloudUpload,
                    onClick = { vm.firebaseSyncNow() },
                    isLast = true,
                )
            }
            Spacer(Modifier.height(16.dp))

            GroupedList(header = "Diagnostics — all off") {
                GroupedRow(
                    title = "Anonymous usage analytics",
                    subtitle = "Coarse sync counts only; no text, no content",
                    icon = Icons.Outlined.Security,
                    trailing = { AuraSwitch(checked = settings.firebaseAnalyticsEnabled, onCheckedChange = { vm.setFirebaseToggle("analytics", it) }) },
                    showChevron = false,
                )
                GroupedRow(
                    title = "Crash reporting",
                    subtitle = "Stack traces via Firebase Crashlytics",
                    icon = Icons.Outlined.Warning,
                    trailing = { AuraSwitch(checked = settings.firebaseCrashlyticsEnabled, onCheckedChange = { vm.setFirebaseToggle("crashlytics", it) }) },
                    showChevron = false,
                )
                GroupedRow(
                    title = "Session history sync",
                    subtitle = "Goals, outcomes and the hash-only MCP audit log",
                    icon = Icons.Outlined.Article,
                    trailing = { AuraSwitch(checked = settings.firebaseSessionSyncEnabled, onCheckedChange = { vm.setFirebaseToggle("sessions", it) }) },
                    showChevron = false,
                    isLast = true,
                )
            }
            Spacer(Modifier.height(16.dp))

            SurfaceCard {
                Text(
                    "What leaves your device: nothing, until you link an account and tap Sync now. " +
                        "Session history uploads only if you enable it above. Server-side rules " +
                        "(firebase/firestore.rules) let each user read and write only their own " +
                        "users/<uid>/snapshots documents; no other collection is reachable. " +
                        "The rules are deployed by you — see firebase/README.md.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}

@Composable
fun B2Screen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val message by vm.b2Message.collectAsStateWithLifecycle()
    var bucket by remember(settings.b2BucketName) { mutableStateOf(settings.b2BucketName) }
    var endpoint by remember(settings.b2Endpoint) { mutableStateOf(settings.b2Endpoint.ifBlank { "s3.us-east-005.backblazeb2.com" }) }
    var keyId by remember { mutableStateOf("") }
    var applicationKey by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Backblaze B2", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Private object storage for files the agent uploads, session screenshots and export bundles. Off by default.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))

            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Status", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                    Spacer(Modifier.weight(1f))
                    Badge(
                        if (vm.b2Configured) "connected" else "off",
                        tone = if (vm.b2Configured) ChipTone.Live else ChipTone.Broken,
                    )
                }
                if (vm.b2Configured) {
                    Spacer(Modifier.height(4.dp))
                    Text("Bucket ${settings.b2BucketName}", style = MonoStyleSmall, color = mono.textSecondary)
                    Text("Endpoint ${settings.b2Endpoint}", style = MonoStyleSmall, color = mono.textSecondary)
                    vm.b2MaskedKey?.let {
                        Spacer(Modifier.height(2.dp))
                        Text("Application key $it (stored encrypted)", style = MonoStyleSmall, color = mono.textSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Test connection", { vm.testB2Connection() })
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        message,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Connected") || message.startsWith("Exported")) mono.successDot else mono.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            SurfaceCard {
                Text(
                    "Credentials",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = mono.textPrimary,
                )
                Spacer(Modifier.height(8.dp))
                AuraTextField(value = bucket, onValueChange = { bucket = it }, placeholder = "Bucket name, e.g. aura-lite-storage", textStyle = MonoStyle)
                Spacer(Modifier.height(8.dp))
                AuraTextField(value = endpoint, onValueChange = { endpoint = it }, placeholder = "s3.us-east-005.backblazeb2.com", textStyle = MonoStyle)
                Spacer(Modifier.height(8.dp))
                AuraTextField(value = keyId, onValueChange = { keyId = it }, placeholder = "Application key id (blank keeps saved)", textStyle = MonoStyle)
                Spacer(Modifier.height(8.dp))
                AuraTextField(
                    value = applicationKey,
                    onValueChange = { applicationKey = it },
                    placeholder = "Application key (blank keeps saved)",
                    textStyle = MonoStyle,
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton(
                        "Save & test",
                        {
                            vm.saveB2Credentials(bucket, endpoint, keyId, applicationKey)
                            keyId = ""
                            applicationKey = ""
                        },
                        modifier = Modifier.weight(1f),
                        enabled = bucket.isNotBlank() && endpoint.isNotBlank(),
                    )
                    SecondaryButton("Clear", { vm.clearB2Credentials() }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))

            GroupedList(header = "Automatic uploads") {
                GroupedRow(
                    title = "Back up session screenshots",
                    subtitle = "Session screenshots upload to B2 when a run ends",
                    icon = Icons.Outlined.CloudUpload,
                    trailing = { AuraSwitch(checked = settings.b2SessionBackupEnabled, onCheckedChange = { vm.setB2SessionBackup(it) }) },
                    showChevron = false,
                    isLast = true,
                )
            }
            Spacer(Modifier.height(16.dp))

            SurfaceCard {
                Text(
                    "Set up a private bucket and a dedicated application key with only " +
                        "listBuckets, listFiles, readFiles and writeFiles — never the Master Application Key. " +
                        "The key id and key are stored in EncryptedSharedPreferences on this device only; " +
                        "they are never committed or bundled into any build. Screenshots and exports " +
                        "upload only when you enable backup or run upload_file / an export from a session.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}
