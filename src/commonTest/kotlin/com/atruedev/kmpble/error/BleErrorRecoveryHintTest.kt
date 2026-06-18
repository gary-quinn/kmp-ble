package com.atruedev.kmpble.error

import kotlin.test.Test
import kotlin.test.assertTrue

class BleErrorRecoveryHintTest {
    @Test
    fun connectionFailedHasRecoveryHint() {
        val error = ConnectionFailed("timeout")
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun connectionLostHasRecoveryHint() {
        val error = ConnectionLost("supervision timeout")
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun gattErrorHasRecoveryHint() {
        val error = GattError("read", GattStatus.Failure)
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun authenticationFailedHasRecoveryHint() {
        val error = AuthenticationFailed("pairing rejected")
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun encryptionFailedHasRecoveryHint() {
        val error = EncryptionFailed("insufficient encryption")
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun mtuExceededHasRecoveryHint() {
        val error = MtuExceeded(512, 23)
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun staleGattHandleHasRecoveryHint() {
        val error = StaleGattHandle("characteristic", "2a19")
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun operationFailedHasRecoveryHint() {
        val error = OperationFailed("unknown")
        assertTrue(error.recoveryHint.isNotEmpty())
    }

    @Test
    fun recoveryHintCanBeOverridden() {
        val custom = "Custom recovery message"
        val error = ConnectionFailed("timeout", recoveryHint = custom)
        assertTrue(error.recoveryHint == custom)
    }
}
