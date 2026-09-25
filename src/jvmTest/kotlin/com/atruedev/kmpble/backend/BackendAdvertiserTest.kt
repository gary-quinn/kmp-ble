package com.atruedev.kmpble.backend

import app.cash.turbine.test
import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.l2cap.L2capException
import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.AdvertiseInterval
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiserException
import com.atruedev.kmpble.server.ExtendedAdvertiseConfig
import com.atruedev.kmpble.server.PeriodicAdvertisingParameters
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(KmpBleBackendApi::class, ExperimentalBleApi::class, ExperimentalUuidApi::class)
class BackendAdvertiserTest {
    private val service = Uuid.parse("0000feed-0000-1000-8000-00805f9b34fb")

    @Test
    fun advertiserStartsOneLegacySet() =
        runBlocking<Unit> {
            val transport = FakeAdvertiserTransport()
            val advertiser = FakeBackend().apply { advertiser = transport }.newAdvertiser()

            advertiser.startAdvertising(
                AdvertiseConfig(name = "kmp", serviceUuids = listOf(service), mode = AdvertiseMode.LowLatency),
            )
            assertTrue(advertiser.isAdvertising.value)
            val (setId, spec) = transport.started.single()
            assertEquals(0, setId)
            assertTrue(spec.legacy)
            assertEquals("kmp", spec.name)
            assertEquals(AdvertiseMode.LowLatency, spec.mode)
            assertFailsWith<AdvertiserException.AlreadyAdvertising> { advertiser.startAdvertising(AdvertiseConfig()) }

            advertiser.stopAdvertising()
            assertFalse(advertiser.isAdvertising.value)
            assertEquals(listOf(0), transport.stopped.toList())
            advertiser.close()
            assertTrue(transport.closed)
            assertFailsWith<AdvertiserException.StartFailed> { advertiser.startAdvertising(AdvertiseConfig()) }
        }

    @Test
    fun startFailureIsWrapped() =
        runBlocking<Unit> {
            val transport = FakeAdvertiserTransport().apply { startFailure = IllegalStateException("busy") }
            val advertiser = FakeBackend().apply { advertiser = transport }.newAdvertiser()
            assertFailsWith<AdvertiserException.StartFailed> { advertiser.startAdvertising(AdvertiseConfig()) }
            assertFalse(advertiser.isAdvertising.value)
        }

    @Test
    fun extendedAdvertiserHonoursSetLimitAndRejectsPeriodic() =
        runBlocking<Unit> {
            val transport = FakeAdvertiserTransport(maxAdvertisingSets = 2)
            val advertiser = FakeBackend().apply { advertiser = transport }.newExtendedAdvertiser()

            advertiser.activeSets.test {
                assertEquals(emptySet(), awaitItem())
                val first =
                    advertiser.startAdvertisingSet(
                        ExtendedAdvertiseConfig(
                            serviceData = mapOf(service to byteArrayOf(5)),
                            secondaryPhy = Phy.Le2M,
                            interval = AdvertiseInterval.LowPower,
                        ),
                    )
                assertEquals(setOf(first), awaitItem())
                val second = advertiser.startAdvertisingSet(ExtendedAdvertiseConfig())
                assertEquals(setOf(first, second), awaitItem())
                assertFailsWith<AdvertiserException.StartFailed> {
                    advertiser.startAdvertisingSet(
                        ExtendedAdvertiseConfig(),
                    )
                }

                advertiser.stopAdvertisingSet(first)
                assertEquals(setOf(second), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            val spec = transport.started.first().second
            assertFalse(spec.legacy)
            assertEquals(Phy.Le2M, spec.secondaryPhy)
            assertEquals(AdvertiseMode.LowPower, spec.mode)
            assertContentEquals(byteArrayOf(5), spec.serviceData.getValue(service))
            assertFailsWith<AdvertiserException.NotSupported> {
                advertiser.startAdvertisingSet(
                    ExtendedAdvertiseConfig(periodicAdvertising = PeriodicAdvertisingParameters()),
                )
            }
            advertiser.close()
        }

    @Test
    fun singleSetBackendsAdvertiseLegacyPdus() =
        runBlocking<Unit> {
            val transport = FakeAdvertiserTransport(maxAdvertisingSets = 1)
            val advertiser = FakeBackend().apply { advertiser = transport }.newExtendedAdvertiser()
            advertiser.startAdvertisingSet(ExtendedAdvertiseConfig(secondaryPhy = Phy.LeCoded))
            assertTrue(
                transport.started
                    .single()
                    .second.legacy,
            )
            assertEquals(
                null,
                transport.started
                    .single()
                    .second.secondaryPhy,
            )
            advertiser.close()
        }

    @Test
    fun l2capListenerPublishesAndAcceptsChannels() =
        runBlocking<Unit> {
            val transport = FakeL2capListenerTransport()
            val listener = FakeBackend().apply { l2capListener = transport }.newL2capListener()

            listener.incoming.test {
                listener.open(secure = true, mtu = null)
                assertEquals(0x80, listener.psm)
                assertTrue(listener.isOpen.value)
                transport.onAccept!!.invoke(FakeL2capStream(0x80))
                val channel = awaitItem()
                assertEquals(0x80, channel.psm)
                assertTrue(channel.isOpen)
                channel.close()
                cancelAndIgnoreRemainingEvents()
            }
            assertFailsWith<L2capException.InvalidState> { listener.open() }
            listener.close()
            assertTrue(transport.closed)
            assertFalse(listener.isOpen.value)
        }

    @Test
    fun l2capListenerPublishFailureClosesTransport() =
        runBlocking<Unit> {
            val transport = FakeL2capListenerTransport().apply { publishFailure = IllegalStateException("denied") }
            val listener = FakeBackend().apply { l2capListener = transport }.newL2capListener()
            assertFailsWith<L2capException.PublishFailed> { listener.open() }
            assertTrue(transport.closed)
        }
}
