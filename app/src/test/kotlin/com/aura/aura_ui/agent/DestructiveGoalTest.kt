package com.aura.aura_ui.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveGoalTest {

    @Test
    fun `money goals are destructive`() {
        assertTrue(DestructiveGoal.isDestructive("pay the bill for the family plan"))
        assertTrue(DestructiveGoal.isDestructive("buy the book Nolan recommended"))
        assertTrue(DestructiveGoal.isDestructive("book a cab to the airport"))
    }

    @Test
    fun `message goals are destructive`() {
        assertTrue(DestructiveGoal.isDestructive("send the email to the team"))
        assertTrue(DestructiveGoal.isDestructive("post the update on the company page"))
        assertTrue(DestructiveGoal.isDestructive("text Mom when I'm on the train"))
    }

    @Test
    fun `deleting goals are destructive`() {
        assertTrue(DestructiveGoal.isDestructive("delete the screenshot folder"))
        assertTrue(DestructiveGoal.isDestructive("uninstall the game"))
        assertTrue(DestructiveGoal.isDestructive("wipe the downloads"))
    }

    @Test
    fun `safe goals are not destructive`() {
        assertFalse(DestructiveGoal.isDestructive("what's on my screen"))
        assertFalse(DestructiveGoal.isDestructive("read my notifications"))
        assertFalse(DestructiveGoal.isDestructive("open maps"))
        assertFalse(DestructiveGoal.isDestructive("how many unread messages do I have"))
    }

    @Test
    fun `reason is provided for destructive classes`() {
        assertTrue(DestructiveGoal.reason("pay the bill").isNotBlank())
        assertTrue(DestructiveGoal.reason("delete the cache").isNotBlank())
        assertTrue(DestructiveGoal.reason("send the email").isNotBlank())
        assertTrue(DestructiveGoal.reason("call the dentist").isNotBlank())
        assertTrue(DestructiveGoal.reason("why").isNotBlank())
    }
}
