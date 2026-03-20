package com.atruedev.kmpble.connection

import kotlin.test.Test
import kotlin.test.assertEquals

class StateRestorationConfigTest {

    @OptIn(com.atruedev.kmpble.ExperimentalBleApi::class)
    @Test
    fun `config stores identifier`() {
        val config = StateRestorationConfig(identifier = "com.myapp.ble.central")
        assertEquals("com.myapp.ble.central", config.identifier)
    }

    @OptIn(com.atruedev.kmpble.ExperimentalBleApi::class)
    @Test
    fun `config equality by identifier`() {
        val a = StateRestorationConfig(identifier = "com.myapp.ble")
        val b = StateRestorationConfig(identifier = "com.myapp.ble")
        assertEquals(a, b)
    }
}
