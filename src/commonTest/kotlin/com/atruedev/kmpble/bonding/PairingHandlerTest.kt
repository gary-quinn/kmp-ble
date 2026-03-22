package com.atruedev.kmpble.bonding

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.Identifier
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalBleApi::class)
class PairingHandlerTest {
    private val testDevice = Identifier("AA:BB:CC:DD:EE:FF")

    @Test
    fun numericComparisonEventCarriesValue() {
        val event = PairingEvent.NumericComparison(testDevice, 123456)
        assertEquals(123456, event.numericValue)
        assertEquals(testDevice, event.deviceIdentifier)
    }

    @Test
    fun passkeyRequestIdentifiesDevice() {
        val event = PairingEvent.PasskeyRequest(testDevice)
        assertEquals(testDevice, event.deviceIdentifier)
    }

    @Test
    fun justWorksConfirmationIdentifiesDevice() {
        val event = PairingEvent.JustWorksConfirmation(testDevice)
        assertEquals(testDevice, event.deviceIdentifier)
    }

    @Test
    fun outOfBandRequestIdentifiesDevice() {
        val event = PairingEvent.OutOfBandDataRequest(testDevice)
        assertEquals(testDevice, event.deviceIdentifier)
    }

    @Test
    fun passkeyNotificationCarriesPasskey() {
        val event = PairingEvent.PasskeyNotification(testDevice, 999999)
        assertEquals(999999, event.passkey)
    }

    @Test
    fun confirmResponseCarriesAccepted() {
        val accept = PairingResponse.Confirm(true)
        val reject = PairingResponse.Confirm(false)
        assertTrue(accept.accepted)
        assertTrue(!reject.accepted)
    }

    @Test
    fun providePinResponseCarriesPin() {
        val response = PairingResponse.ProvidePin(123456)
        assertEquals(123456, response.pin)
    }

    @Test
    fun oobDataResponseCarriesData() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val response = PairingResponse.ProvideOobData(data)
        assertTrue(response.data.contentEquals(data))
    }

    @Test
    fun handlerReceivesNumericComparison() =
        runTest {
            var receivedEvent: PairingEvent? = null
            val handler =
                PairingHandler { event ->
                    receivedEvent = event
                    PairingResponse.Confirm(true)
                }

            val event = PairingEvent.NumericComparison(testDevice, 654321)
            val response = handler.onPairingEvent(event)

            assertIs<PairingResponse.Confirm>(response)
            assertTrue(response.accepted)
            assertIs<PairingEvent.NumericComparison>(receivedEvent)
            assertEquals(654321, (receivedEvent as PairingEvent.NumericComparison).numericValue)
        }

    @Test
    fun handlerReceivesPasskeyRequest() =
        runTest {
            val handler = PairingHandler { PairingResponse.ProvidePin(112233) }
            val response = handler.onPairingEvent(PairingEvent.PasskeyRequest(testDevice))
            assertIs<PairingResponse.ProvidePin>(response)
            assertEquals(112233, response.pin)
        }

    @Test
    fun allPairingEventsAreSealed() {
        val events: List<PairingEvent> =
            listOf(
                PairingEvent.NumericComparison(testDevice, 0),
                PairingEvent.PasskeyRequest(testDevice),
                PairingEvent.JustWorksConfirmation(testDevice),
                PairingEvent.OutOfBandDataRequest(testDevice),
                PairingEvent.PasskeyNotification(testDevice, 0),
            )
        assertEquals(5, events.size)
    }

    @Test
    fun allPairingResponsesAreSealed() {
        val responses: List<PairingResponse> =
            listOf(
                PairingResponse.Confirm(true),
                PairingResponse.ProvidePin(0),
                PairingResponse.ProvideOobData(byteArrayOf()),
            )
        assertEquals(3, responses.size)
    }
}
