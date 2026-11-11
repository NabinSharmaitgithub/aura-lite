package com.aura.aura_ui.automation

import android.content.Context
import android.content.Intent
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

    fun installedApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .map { resolve ->
                    AppEntry(
                        label = resolve.loadLabel(pm).toString(),
                        packageName = resolve.activityInfo.packageName,
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun lookup(query: String, limit: Int = 8): List<AppEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return installedApps()
            .filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            .sortedWith(
                compareByDescending<AppEntry> { it.label.lowercase() == q }
                    .thenByDescending { it.label.lowercase().startsWith(q) }
                    .thenBy { it.label.length },
            )
            .take(limit)
    }

    fun launch(appName: String?, packageName: String?): ToolResult {
        val target = when {
            packageName != null -> installedApps().firstOrNull { it.packageName == packageName }
                ?: AppEntry(packageName, packageName)
            appName != null -> lookup(appName).firstOrNull()
            else -> null
        } ?: return ToolResult.fail(ToolError.notFound("App not found"))
        val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
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
