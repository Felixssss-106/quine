package com.quine.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {
    @Test
    fun `trust level falls back to standard`() {
        assertEquals(TrustLevel.STANDARD, TrustLevel.fromId(null))
        assertEquals(TrustLevel.STANDARD, TrustLevel.fromId("nonsense"))
        assertEquals(TrustLevel.RUNAWAY, TrustLevel.fromId("runaway"))
    }

    @Test
    fun `icon variant falls back to light`() {
        assertEquals(IconVariant.LIGHT, IconVariant.fromId(null))
        assertEquals(IconVariant.DARK, IconVariant.fromId("dark"))
    }
}
