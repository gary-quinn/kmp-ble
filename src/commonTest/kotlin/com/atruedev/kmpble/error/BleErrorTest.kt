package com.atruedev.kmpble.error

import com.atruedev.kmpble.error.StaleGattHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BleErrorTest {

    @Test
    fun staleGattHandleCanBeCreatedAndThrown() {
        val error = StaleGattHandle("characteristic", "00002a19-0000-1000-8000-00805f9b34fb")
        assertEquals("characteristic", error.handleType)
        assertEquals("00002a19-0000-1000-8000-00805f9b34fb", error.uuid)
    }

    @Test
    fun staleGattHandleWrappedInBleException() {
        val ex = assertFailsWith<BleException> {
            throw BleException(StaleGattHandle("descriptor", "00002902-0000-1000-8000-00805f9b34fb"))
        }
        assertEquals(StaleGattHandle("descriptor", "00002902-0000-1000-8000-00805f9b34fb"), ex.error)
    }

    @Test
    fun staleGattHandleImplementsGattOperationError() {
        val error = StaleGattHandle("characteristic", "00002a19-0000-1000-8000-00805f9b34fb")
        // StaleGattHandle implements GattOperationError via sealed interface hierarchy
        assertEquals(true, error is GattOperationError)
    }
}
