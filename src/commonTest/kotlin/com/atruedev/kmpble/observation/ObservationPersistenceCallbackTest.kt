package com.atruedev.kmpble.observation

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.internal.ObservationKey
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests that ObservationManager fires the onObservationsChanged callback
 * when observations are added or removed. This callback is used by
 * iOS state restoration to persist observations to NSUserDefaults.
 */
@OptIn(ExperimentalUuidApi::class)
class ObservationPersistenceCallbackTest {
    private val serviceUuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val charUuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
    private val charUuid2 = Uuid.parse("00002a38-0000-1000-8000-00805f9b34fb")

    @Test
    fun `callback fires on subscribe with correct keys and backpressure`() =
        runTest {
            val manager = ObservationManager()
            val received = mutableListOf<Set<PersistedObservation>>()
            manager.onObservationsChanged = { obs -> received.add(obs) }

            manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)

            assertEquals(1, received.size)
            assertEquals(
                setOf(PersistedObservation(ObservationKey(serviceUuid, charUuid), BackpressureStrategy.Latest)),
                received[0],
            )
        }

    @Test
    fun `callback preserves buffer backpressure strategy`() =
        runTest {
            val manager = ObservationManager()
            val received = mutableListOf<Set<PersistedObservation>>()
            manager.onObservationsChanged = { obs -> received.add(obs) }

            manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Buffer(32))

            assertEquals(1, received.size)
            val obs = received[0].single()
            assertEquals(BackpressureStrategy.Buffer(32), obs.backpressure)
        }

    @Test
    fun `callback fires on unsubscribe`() =
        runTest {
            val manager = ObservationManager()
            val received = mutableListOf<Set<PersistedObservation>>()

            manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)
            manager.onObservationsChanged = { obs -> received.add(obs) }

            manager.unsubscribe(serviceUuid, charUuid)

            assertEquals(1, received.size)
            assertTrue(received[0].isEmpty())
        }

    @Test
    fun `callback includes all active observations`() =
        runTest {
            val manager = ObservationManager()
            val received = mutableListOf<Set<PersistedObservation>>()
            manager.onObservationsChanged = { obs -> received.add(obs) }

            manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)
            manager.subscribe(serviceUuid, charUuid2, BackpressureStrategy.Buffer(16))

            assertEquals(2, received.size)
            assertEquals(
                setOf(
                    PersistedObservation(ObservationKey(serviceUuid, charUuid), BackpressureStrategy.Latest),
                    PersistedObservation(ObservationKey(serviceUuid, charUuid2), BackpressureStrategy.Buffer(16)),
                ),
                received[1],
            )
        }

    @Test
    fun `no callback when callback is null`() =
        runTest {
            val manager = ObservationManager()
            // Should not throw - callback is null by default
            manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)
            manager.unsubscribe(serviceUuid, charUuid)
        }

    @Test
    fun `no callback when key set unchanged on duplicate subscribe`() =
        runTest {
            val manager = ObservationManager()
            manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)

            val received = mutableListOf<Set<PersistedObservation>>()
            manager.onObservationsChanged = { obs -> received.add(obs) }

            // Second subscribe for same key - collector count goes up, but key set unchanged
            manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)

            assertTrue(received.isEmpty(), "Callback should not fire when key set is unchanged")
        }
}
