package com.aura.aura_ui.automation

import com.aura.aura_ui.state.InstallState

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class AppEntry(
    val label: String,
    val packageName: String,
)

@Singleton
class AppResolver @Inject constructor(@ApplicationContext private val context: Context) {

    private val knownPackageAliases = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "whats app" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "wa" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "instagram" to listOf("com.instagram.android"),
        "messenger" to listOf("com.facebook.orca"),
        "facebook" to listOf("com.facebook.katana"),
        "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web"),
        "gmail" to listOf("com.google.android.gm"),
        "chrome" to listOf("com.android.chrome"),
        "maps" to listOf("com.google.android.apps.maps"),
        "youtube" to listOf("com.google.android.youtube"),
        "free fire" to listOf("com.garena.game.freefire"),
        "freefire" to listOf("com.garena.game.freefire"),
        "free fire max" to listOf("com.garena.game.freefiremax"),
    )

    fun installedApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            val launcherApps = pm.queryIntentActivities(intent, 0)
                .map { resolve ->
                    AppEntry(
                        label = resolve.loadLabel(pm).toString(),
                        packageName = resolve.activityInfo.packageName,
                    )
                }
            // Fallback: include known common apps if not found via launcher query (some OEMs restrict)
            val knownCandidates = listOf(
                "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
                "com.instagram.android", "com.facebook.orca", "com.facebook.katana",
            )
            val additional = knownCandidates.mapNotNull { pkg ->
                if (launcherApps.any { it.packageName == pkg }) return@mapNotNull null
                runCatching {
                    val appInfo: ApplicationInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    if (pm.getLaunchIntentForPackage(pkg) != null) {
                        AppEntry(label = label, packageName = pkg)
                    } else null
                }.getOrNull()
            }
            (launcherApps + additional)
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun lookup(query: String, limit: Int = 8): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val installed = installedApps()
        // Direct alias match
        val aliasPackages = knownPackageAliases.entries.firstOrNull { (key, _) ->
            q == key || q.contains(key) || key.contains(q)
        }?.value.orEmpty()
        val aliasMatches = aliasPackages.mapNotNull { pkg ->
            installed.firstOrNull { it.packageName == pkg } ?: runCatching {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                if (pm.getLaunchIntentForPackage(pkg) != null) AppEntry(label, pkg) else null
            }.getOrNull()
        }
        val matches = installed
            .filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        return (aliasMatches + matches)
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<AppEntry> { app ->
                    aliasMatches.any { it.packageName == app.packageName } ||
                        app.label.lowercase() == q ||
                        app.packageName.lowercase().contains(q)
                }
                    .thenByDescending { it.label.lowercase().startsWith(q) }
                    .thenBy { it.label.length },
            )
            .take(limit)
    }

    fun launch(appName: String?, packageName: String?): ToolResult {
        val pm = context.packageManager
        val target = when {
            packageName != null -> {
                val resolved = installedApps().firstOrNull { it.packageName == packageName }
                resolved ?: AppEntry(packageName = packageName, label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName))
            }
            appName != null -> {
                val lookedUp = lookup(appName).firstOrNull()
                lookedUp ?: run {
                    val q = appName.trim().lowercase()
                    val candidates = knownPackageAliases.entries.firstOrNull { (key, _) ->
                        q.contains(key) || key.contains(q)
                    }?.value.orEmpty()
                    candidates.firstNotNullOfOrNull { pkg ->
                        if (pm.getLaunchIntentForPackage(pkg) != null) {
                            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                            AppEntry(label, pkg)
                        } else null
                    }
                }
            }
            else -> null
        } ?: return ToolResult.fail(ToolError.notFound("App not found"))
        val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
            ?: return ToolResult.fail(ToolError.notFound("${target.label} has no launch activity"))
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching {
            context.startActivity(launchIntent)
            ToolResult.ok(
                changed = true,
                observed = buildJsonObject {
                    put("package", target.packageName)
                    put("label", target.label)
                },
            )
        }.getOrElse { ToolResult.fail(ToolError.internal(it.message ?: "Launch failed")) }
    }

    /** Google Play Store listing URI for a package (the install entry point for not-yet-installed apps). */
    fun playStoreListingUri(packageName: String): String = "https://play.google.com/store/apps/details?id=$packageName"

    fun install(appName: String?, packageName: String?): ToolResult {
        InstallFlow.update(InstallState.RESOLVING)
        val requested = packageName?.takeIf { it.isNotBlank() } ?: appName?.takeIf { it.isNotBlank() }
            ?: return ToolResult.fail(ToolError.notFound("app_name or package is required to install an app"))
                .also { InstallFlow.update(InstallState.FAILED) }
        val target = resolveInstallPackage(requested)
            ?: return ToolResult.fail(ToolError.notFound("Could not resolve \"$requested\" to an Android package for install"))
                .also { InstallFlow.update(InstallState.UNRESOLVED) }
        val uri = playStoreListingUri(target)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = context.packageManager.resolveActivity(intent, 0)
            ?: return ToolResult.fail(ToolError.notFound("No app can open the Play Store listing for $target"))
                .also { InstallFlow.update(InstallState.FAILED) }
        return runCatching {
            context.startActivity(intent)
            InstallFlow.update(InstallState.LISTING_OPENED)
            ToolResult.ok(
                changed = true,
                observed = buildJsonObject {
                    put("package", target)
                    put("uri", uri)
                    put(
                        "label",
                        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(target, 0)).toString() }.getOrDefault(target),
                    )
                    put("note", "Opened the Google Play Store listing; the install action itself happens in the Play Store UI (tap Install) and the user must confirm it.")
                },
            )
        }.getOrElse {
            InstallFlow.update(InstallState.FAILED)
            ToolResult.fail(ToolError.internal(it.message ?: "Could not open the Play Store listing"))
        }
    }

    /** Resolve an install target: exact package, known alias hit, or the raw query as a package id. */
    private fun resolveInstallPackage(requested: String): String? {
        val q = requested.trim().lowercase()
        val aliasHit = knownPackageAliases.entries.firstOrNull { (key, _) -> q == key || q.contains(key) || key.contains(q) }?.value.orEmpty()
        val candidates = (aliasHit.ifEmpty { listOf(q) } + listOf(q)).distinct()
        return candidates.firstOrNull { it.contains('.') && it.isNotBlank() }
    }

    fun lookupResult(query: String): ToolResult {
        val apps = lookup(query)
        return ToolResult.ok(
            observed = buildJsonObject {
                put(
                    "apps",
                    buildJsonArray {
                        apps.forEach { app ->
                            add(buildJsonObject {
                                put("label", app.label)
                                put("package", app.packageName)
                            })
                        }
                    },
                )
                put("count", apps.size)
            },
        )
    }

    fun openDeeplink(uri: String, packageName: String?): ToolResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageName != null) setPackage(packageName)
        }
        val resolved = context.packageManager.resolveActivity(intent, 0)
            ?: return ToolResult.fail(ToolError.notFound("No app can open $uri"))
        return runCatching {
            context.startActivity(intent)
            ToolResult.ok(
                changed = true,
                observed = buildJsonObject {
                    put("uri", uri)
                    put("package", resolved.activityInfo?.packageName ?: packageName.orEmpty())
                },
            )
        }.getOrElse { ToolResult.fail(ToolError.internal(it.message ?: "Could not open link")) }
    }

    fun resolveDeeplink(uri: String): ToolResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
        return ToolResult.ok(
            observed = buildJsonObject {
                put(
                    "handlers",
                    buildJsonArray {
                        handlers.forEach { info ->
                            add(buildJsonObject {
                                put("package", info.activityInfo.packageName)
                                put("activity", info.activityInfo.name)
                                put("label", info.loadLabel(context.packageManager).toString())
                            })
                        }
                    },
                )
            },
        )
    }

    fun listAppDeeplinks(packageName: String): ToolResult {
        val catalog = runCatching {
            val raw = context.assets.open("deeplink_catalog.json").bufferedReader().use { it.readText() }
            val root = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
            (root["apps"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        }.getOrDefault(emptyList())
        val matches = catalog.filter {
            (it["package"] as? kotlinx.serialization.json.JsonPrimitive)?.content == packageName
        }
        return ToolResult.ok(
            observed = buildJsonObject {
                put("package", packageName)
                put("links", buildJsonArray { matches.forEach { add(it["links"] ?: it) } })
            },
        )
    }
}
