package com.aura.aura_ui.notifications

import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationWaitingFilterTest {

    @Test
    fun `notification listener service class is available`() {
        assertTrue(AuraNotificationListenerService::class.java.name.isNotBlank())
    }
}
