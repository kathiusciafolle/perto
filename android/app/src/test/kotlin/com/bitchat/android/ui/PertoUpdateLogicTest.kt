package com.bitchat.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PertoUpdateLogicTest {

    @Test
    fun `a newer release is offered and the same or older one is not`() {
        assertTrue(shouldOfferUpdate("0.1.3", "0.1.4"))
        assertTrue(shouldOfferUpdate("0.1.9", "0.2.0"))
        assertTrue(shouldOfferUpdate("0.1.3", "v0.1.10"))
        assertFalse(shouldOfferUpdate("0.1.4", "0.1.4"))
        assertFalse(shouldOfferUpdate("0.1.4", "0.1.3"))
        assertFalse(shouldOfferUpdate("0.1.4", null))
    }

    @Test
    fun `automatic check waits for the interval and runs again after it`() {
        val hour = 60 * 60 * 1000L
        val now = 1_700_000_000_000L
        assertTrue(isAutoCheckDue(now, lastCheckMillis = 0L, intervalMillis = 6 * hour))
        assertFalse(isAutoCheckDue(now, lastCheckMillis = now - hour, intervalMillis = 6 * hour))
        assertTrue(isAutoCheckDue(now, lastCheckMillis = now - 7 * hour, intervalMillis = 6 * hour))
        // Relógio voltou no tempo: confere de novo em vez de esperar para sempre.
        assertTrue(isAutoCheckDue(now, lastCheckMillis = now + hour, intervalMillis = 6 * hour))
    }
}
