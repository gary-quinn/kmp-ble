package com.atruedev.kmpble.adapter

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BleCapabilitiesTest {
    @Test
    fun none_hasAllFalse() {
        val caps = BleCapabilities.None
        assertFalse(caps.supportsExtendedAdvertising)
        assertFalse(caps.supportsLe2mPhy)
        assertFalse(caps.supportsLeCodedPhy)
        assertFalse(caps.supportsPeriodicAdvertising)
        assertFalse(caps.supportsLePowerControl)
        assertFalse(caps.supportsLeAudio)
        assertFalse(caps.supportsConnectionSubrating)
    }

    @Test
    fun construction_preservesValues() {
        val caps =
            BleCapabilities(
                supportsExtendedAdvertising = true,
                supportsLe2mPhy = true,
                supportsLeCodedPhy = false,
                supportsPeriodicAdvertising = true,
                supportsLePowerControl = false,
                supportsLeAudio = true,
                supportsConnectionSubrating = false,
            )
        assertTrue(caps.supportsExtendedAdvertising)
        assertTrue(caps.supportsLe2mPhy)
        assertFalse(caps.supportsLeCodedPhy)
        assertTrue(caps.supportsPeriodicAdvertising)
        assertFalse(caps.supportsLePowerControl)
        assertTrue(caps.supportsLeAudio)
        assertFalse(caps.supportsConnectionSubrating)
    }

    @Test
    fun toString_includesAllFields() {
        val caps =
            BleCapabilities(
                supportsExtendedAdvertising = true,
                supportsLe2mPhy = false,
                supportsLeCodedPhy = false,
                supportsPeriodicAdvertising = false,
                supportsLePowerControl = false,
                supportsLeAudio = false,
                supportsConnectionSubrating = false,
            )
        val str = caps.toString()
        assertTrue(str.contains("extAdv=true"), "toString should include extAdv")
        assertTrue(str.contains("le2M=false"), "toString should include le2M")
    }
}
