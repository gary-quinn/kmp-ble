package com.atruedev.kmpble.error

import com.atruedev.kmpble.error.StaleGattHandle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BleErrorTest {
    @Test
    fun staleGattHandleCanBeCreatedAndThrown() {
        val error =
            StaleGattHandle(
                "characteristic",
                "00002a19-0000-1000-8000-00805f9b34fb",
            )
        assertEquals("characteristic", error.handleType)
        assertEquals("00002a19-0000-1000-8000-00805f9b34fb", error.uuid)
    }

    @Test
    fun staleGattHandleWrappedInBleException() {
        val ex =
            assertFailsWith<BleException> {
                throw BleException(
                    StaleGattHandle(
                        "descriptor",
                        "00002902-0000-1000-8000-00805f9b34fb",
                    ),
                )
            }
        assertEquals(
            StaleGattHandle(
                "descriptor",
                "00002902-0000-1000-8000-00805f9b34fb",
            ),
            ex.error,
        )
    }

    @Test
    fun staleGattHandleImplementsGattOperationError() {
        val error =
            StaleGattHandle(
                "characteristic",
                "00002a19-0000-1000-8000-00805f9b34fb",
            )
        // StaleGattHandle implements GattOperationError via sealed interface hierarchy
        assertEquals(true, error is GattOperationError)
    }

    @Test
    fun serviceDiscoveryErrorImplementsGattOperationError() {
        val error = ServiceDiscoveryError(status = GattStatus.Failure)
        assertEquals(true, error is GattOperationError)
        assertEquals(true, error is BleError)
    }

    @Test
    fun serviceDiscoveryErrorWithServiceUuid() {
        val error = ServiceDiscoveryError("0000180d-0000-1000-8000-00805f9b34fb", GattStatus.InsufficientAuthentication)
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", error.serviceUuid)
        assertEquals(GattStatus.InsufficientAuthentication, error.status)
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun characteristicErrorImplementsGattOperationError() {
        val error =
            CharacteristicError(
                charUuid = "00002a37-0000-1000-8000-00805f9b34fb",
                operation = "read",
                status = GattStatus.ReadNotPermitted,
            )
        assertEquals(true, error is GattOperationError)
        assertEquals("00002a37-0000-1000-8000-00805f9b34fb", error.charUuid)
        assertEquals("read", error.operation)
        assertEquals(GattStatus.ReadNotPermitted, error.status)
    }

    @Test
    fun connectionFailedHasFailureReasonDefault() {
        val error = ConnectionFailed("test")
        assertEquals(ConnectionFailureReason.UNKNOWN, error.failureReason)
    }

    @Test
    fun connectionFailedWithExplicitFailureReason() {
        val error = ConnectionFailed("timeout", ConnectionFailureReason.TIMEOUT)
        assertEquals(ConnectionFailureReason.TIMEOUT, error.failureReason)
        assertEquals("timeout", error.reason)
    }

    @Test
    fun connectionFailureReasonIsEnum() {
        // Exhaustive when compiles (compile-time check)
        val reasons = ConnectionFailureReason.entries
        assertTrue(reasons.size >= 8)
        assertTrue(ConnectionFailureReason.TIMEOUT in reasons)
        assertTrue(ConnectionFailureReason.LINK_LOSS in reasons)
        assertTrue(ConnectionFailureReason.UNKNOWN_DEVICE in reasons)
        assertTrue(ConnectionFailureReason.GATT_ERROR in reasons)
    }

    @Test
    fun connectionLostUsesLinkLossByDefault() {
        val error = ConnectionLost("remote disconnect")
        assertEquals(ConnectionFailureReason.LINK_LOSS, error.failureReason)
    }
}
