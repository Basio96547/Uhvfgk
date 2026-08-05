package com.basel.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalGuardTest {

    @Test
    fun `caps threads well below the core count when cool`() {
        // Snapdragon 8 Elite reports 8 cores and has no efficiency cores, so
        // using them all heats the phone far faster than it gains tokens.
        assertEquals(6, ThermalGuard.threadBudget(coreCount = 8, level = ThermalLevel.NORMAL))
    }

    @Test
    fun `backs off as the device warms`() {
        assertEquals(4, ThermalGuard.threadBudget(8, ThermalLevel.WARM))
        assertEquals(2, ThermalGuard.threadBudget(8, ThermalLevel.HOT))
        assertEquals(2, ThermalGuard.threadBudget(8, ThermalLevel.CRITICAL))
    }

    @Test
    fun `always leaves headroom on smaller devices`() {
        assertEquals(2, ThermalGuard.threadBudget(4, ThermalLevel.NORMAL))
    }

    @Test
    fun `never returns fewer than one thread`() {
        assertEquals(1, ThermalGuard.threadBudget(2, ThermalLevel.NORMAL))
        assertEquals(1, ThermalGuard.threadBudget(1, ThermalLevel.CRITICAL))
    }

    @Test
    fun `only pauses generation once the system is throttling`() {
        assertFalse(ThermalGuard.shouldPause(ThermalLevel.NORMAL))
        assertFalse(ThermalGuard.shouldPause(ThermalLevel.WARM))
        assertFalse(ThermalGuard.shouldPause(ThermalLevel.HOT))
        assertTrue(ThermalGuard.shouldPause(ThermalLevel.CRITICAL))
    }
}
