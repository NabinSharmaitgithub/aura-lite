package com.aura.aura_ui.agent

import android.app.NotificationManager
import android.content.Context
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.mcp.bridge.ApprovalGate
import com.aura.mcp.bridge.ApprovalRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Remote MCP calls raise an approval notification and suspend until the user answers (or two
 * minutes pass, which counts as a denial).
 */
@Singleton
class NotificationApprovalGate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val confirmations: ConfirmationBroker,
) : ApprovalGate {

    override suspend fun approve(request: ApprovalRequest): Boolean {
        AuraNotifications.ensureChannels(context)
        val card = confirmations.request(
            title = "Allow ${request.tool}?",
            detail = "An external MCP client wants to call ${request.tool}.\n\n${request.args}",
            tool = request.tool,
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(
            AuraNotifications.ID_APPROVAL,
            AuraNotifications.approvalNotification(context, card.id, request.tool, card.detail),
        )
        val result = withTimeoutOrNull(120_000) {
            confirmations.await(card).await()
        } ?: false
        manager?.cancel(AuraNotifications.ID_APPROVAL)
        return result
    }
}
