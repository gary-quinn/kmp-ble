package com.atruedev.kmpble.profiles.battery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BatteryLevelTest {

    @Test
    fun parsesValidLevel() {
        assertEquals(75, parseBatteryLevel(byteArrayOf(75)))
    }

    @Test
    fun parsesZero() {
        assertEquals(0, parseBatteryLevel(byteArrayOf(0)))
    }

    @Test
    fun parsesHundred() {
        assertEquals(100, parseBatteryLevel(byteArrayOf(100)))
    }

    @Test
    fun rejectsAboveHundred() {
        assertNull(parseBatteryLevel(byteArrayOf(101)))
    }

    @Test
    fun rejectsEmpty() {
        assertNull(parseBatteryLevel(byteArrayOf()))
    }
}
