package com.infinity.drive.data.repository

import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class TelegramHistoryTest {
    @Test
    fun historyMustAdvanceTowardOlderMessagesOrFinish() {
        assertTrue(isAdvancingHistory(0, 200))
        assertTrue(isAdvancingHistory(200, 100))
        assertTrue(isAdvancingHistory(100, 0))
        assertFalse(isAdvancingHistory(100, 100))
        assertFalse(isAdvancingHistory(100, 200))
        assertFalse(isAdvancingHistory(100, -1))
    }
}
