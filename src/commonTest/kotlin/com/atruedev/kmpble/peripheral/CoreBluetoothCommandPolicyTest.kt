package com.atruedev.kmpble.peripheral

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreBluetoothCommandPolicyTest {
    @Test
    fun centralCommandsRequirePoweredOnAdapter() {
        assertTrue(CoreBluetoothCommandPolicy.canIssueCentralCommand(centralPoweredOn = true))
        assertFalse(CoreBluetoothCommandPolicy.canIssueCentralCommand(centralPoweredOn = false))
    }

    @Test
    fun peripheralCommandsRequirePoweredOnAdapterAndConnectedPeripheral() {
        assertTrue(
            CoreBluetoothCommandPolicy.canIssuePeripheralCommand(
                centralPoweredOn = true,
                peripheralConnected = true,
            ),
        )
        assertFalse(
            CoreBluetoothCommandPolicy.canIssuePeripheralCommand(
                centralPoweredOn = false,
                peripheralConnected = true,
            ),
        )
        assertFalse(
            CoreBluetoothCommandPolicy.canIssuePeripheralCommand(
                centralPoweredOn = true,
                peripheralConnected = false,
            ),
        )
    }

    @Test
    fun disconnectIsSkippedWhenAdapterOffOrPeripheralDisconnected() {
        assertTrue(
            CoreBluetoothCommandPolicy.shouldSkipDisconnect(
                centralPoweredOn = false,
                peripheralDisconnected = false,
            ),
        )
        assertTrue(
            CoreBluetoothCommandPolicy.shouldSkipDisconnect(
                centralPoweredOn = true,
                peripheralDisconnected = true,
            ),
        )
        assertFalse(
            CoreBluetoothCommandPolicy.shouldSkipDisconnect(
                centralPoweredOn = true,
                peripheralDisconnected = false,
            ),
        )
    }
}
