package com.aura.aura_ui.di

import android.content.Context
import com.aura.aura_ui.agent.NotificationApprovalGate
import com.aura.aura_ui.agent.control.ControlLock
import com.aura.aura_ui.bridge.AuraDeviceBridge
import com.aura.aura_ui.data.prefs.RestrictedAppsRepository
import com.aura.mcp.audit.McpAuditLogger
import com.aura.mcp.bridge.ApprovalGate
import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.RestrictedAppsStore
import com.aura.mcp.cache.CvMemo
import com.aura.mcp.cache.LastSeenScreen
import com.aura.mcp.cache.PerceptionCache
import com.aura.mcp.server.InProcessMcpServer
import com.aura.mcp.server.McpCaches
import com.aura.mcp.server.McpClient
import com.aura.mcp.server.McpServerBuilder
import com.aura.mcp.server.McpSinks
import com.aura.mcp.tools.McpToolRegistry
import com.aura.mcp.tools.ToolPipelineHooks
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    @Singleton
    abstract fun bindDeviceBridge(impl: AuraDeviceBridge): DeviceBridge

    @Binds
    @Singleton
    abstract fun bindRestrictedApps(impl: RestrictedAppsRepository): RestrictedAppsStore

    @Binds
    @Singleton
    abstract fun bindApprovalGate(impl: NotificationApprovalGate): ApprovalGate
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
                redactHeader("x-api-key")
                redactHeader("x-goog-api-key")
            },
        )
        .build()

    @Provides
    @Singleton
    fun auditLogger(@ApplicationContext context: Context): McpAuditLogger =
        McpAuditLogger(File(context.filesDir, "mcp/audit.jsonl"))

    @Provides
    @Singleton
    fun perceptionCache(): PerceptionCache = PerceptionCache()

    @Provides
    @Singleton
    fun mcpCaches(perceptionCache: PerceptionCache): McpCaches = McpCaches(
        perception = perceptionCache,
        cvMemo = CvMemo(),
        lastSeen = LastSeenScreen(),
    )

    @Provides
    @Singleton
    fun mcpBuiltServer(
        bridge: DeviceBridge,
        restricted: RestrictedAppsStore,
        approval: ApprovalGate,
        audit: McpAuditLogger,
        caches: McpCaches,
    ): McpServerBuilder.BuiltServer = McpServerBuilder(bridge)
        .restrictedApps(restricted)
        .approvalGate(approval)
        .caches(caches)
        .sinks(
            McpSinks(
                audit = audit,
                postActionObserver = { tool, _, result ->
                    if (!result.ok) return@McpSinks
                    val observed = result.observed ?: return@McpSinks
                    if (tool == "read_screen" || tool == "perceive_screen" || tool == "get_screenshot") {
                        val generation = (observed["generation"] as? kotlinx.serialization.json.JsonPrimitive)
                            ?.content?.toLongOrNull() ?: -1L
                        val signature = (observed["hash"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            ?: (observed["signature"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        val pkg = (observed["package"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            ?: (observed["packageName"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        caches.lastSeen.update(generation, signature, pkg, observed)
                        signature?.let { caches.cvMemo.put(it, observed) }
                    }
                },
            ),
        )
        .hooks(object : ToolPipelineHooks {
            override fun isPaused(): Boolean =
                ControlLock.isUserPaused() || ControlLock.isUserHolding()
        })
        .build(clientId = "local")

    @Provides
    @Singleton
    fun toolRegistry(built: McpServerBuilder.BuiltServer): McpToolRegistry = built.registry

    @Provides
    @Singleton
    fun inProcessServer(built: McpServerBuilder.BuiltServer): InProcessMcpServer = built.inProcess

    @Provides
    @Singleton
    fun mcpClient(server: InProcessMcpServer): McpClient = server.asClient()
}
