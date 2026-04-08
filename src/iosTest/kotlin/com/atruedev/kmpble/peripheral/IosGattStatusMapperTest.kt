package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.GattStatus
import platform.CoreBluetooth.CBATTErrorInsufficientAuthentication
import platform.CoreBluetooth.CBATTErrorInsufficientAuthorization
import platform.CoreBluetooth.CBATTErrorInsufficientEncryption
import platform.CoreBluetooth.CBATTErrorInvalidAttributeValueLength
import platform.CoreBluetooth.CBATTErrorInvalidOffset
import platform.CoreBluetooth.CBATTErrorReadNotPermitted
import platform.CoreBluetooth.CBATTErrorRequestNotSupported
import platform.CoreBluetooth.CBATTErrorSuccess
import platform.CoreBluetooth.CBATTErrorWriteNotPermitted
import platform.Foundation.NSError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosGattStatusMapperTest {
    @Test
    fun nullErrorMapsToSuccess() {
        val error: NSError? = null
        assertEquals(GattStatus.Success, error.toGattStatus())
    }

    @Test
    fun cbAttSuccessMapsToSuccess() {
        assertEquals(GattStatus.Success, attError(CBATTErrorSuccess).toGattStatus())
    }

    @Test
    fun authenticationErrors() {
        assertEquals(
            GattStatus.InsufficientAuthentication,
            attError(CBATTErrorInsufficientAuthentication).toGattStatus(),
        )
        assertEquals(
            GattStatus.InsufficientEncryption,
            attError(CBATTErrorInsufficientEncryption).toGattStatus(),
        )
        assertEquals(
            GattStatus.InsufficientAuthorization,
            attError(CBATTErrorInsufficientAuthorization).toGattStatus(),
        )
    }

    @Test
    fun readWritePermissionErrors() {
        assertEquals(
            GattStatus.ReadNotPermitted,
            attError(CBATTErrorReadNotPermitted).toGattStatus(),
        )
        assertEquals(
            GattStatus.WriteNotPermitted,
            attError(CBATTErrorWriteNotPermitted).toGattStatus(),
        )
    }

    @Test
    fun attributeErrors() {
        assertEquals(
            GattStatus.InvalidOffset,
            attError(CBATTErrorInvalidOffset).toGattStatus(),
        )
        assertEquals(
            GattStatus.InvalidAttributeLength,
            attError(CBATTErrorInvalidAttributeValueLength).toGattStatus(),
        )
        assertEquals(
            GattStatus.RequestNotSupported,
            attError(CBATTErrorRequestNotSupported).toGattStatus(),
        )
    }

    @Test
    fun unknownErrorCodeMapsToUnknownWithPlatformInfo() {
        val result = attError(0xFF).toGattStatus()
        assertIs<GattStatus.Unknown>(result)
        assertEquals(0xFF, result.platformCode)
        assertEquals("ios", result.platformName)
    }

    @Test
    fun nonAttDomainErrorStillMapsOnCode() {
        val cocoaError = NSError.errorWithDomain("NSCocoaErrorDomain", CBATTErrorReadNotPermitted, null)
        assertEquals(GattStatus.ReadNotPermitted, cocoaError.toGattStatus())
    }

    @Test
    fun negativeErrorCodeMapsToUnknown() {
        val result = attError(-1L).toGattStatus()
        assertIs<GattStatus.Unknown>(result)
        assertEquals(-1, result.platformCode)
    }

    @Test
    fun isSuccessForAllGattStatusVariants() {
        assertTrue(GattStatus.Success.isSuccess())

        val nonSuccessStatuses =
            listOf(
                GattStatus.Failure,
                GattStatus.InsufficientAuthentication,
                GattStatus.InsufficientEncryption,
                GattStatus.InsufficientAuthorization,
                GattStatus.InvalidOffset,
                GattStatus.InvalidAttributeLength,
                GattStatus.ReadNotPermitted,
                GattStatus.WriteNotPermitted,
                GattStatus.RequestNotSupported,
                GattStatus.ConnectionCongested,
                GattStatus.Unknown(platformCode = 999, platformName = "test"),
            )
        nonSuccessStatuses.forEach { status ->
            assertFalse(status.isSuccess(), "Expected isSuccess()=false for $status")
        }
    }

    private fun attError(code: Long): NSError = NSError.errorWithDomain("CBATTErrorDomain", code, null)
}
