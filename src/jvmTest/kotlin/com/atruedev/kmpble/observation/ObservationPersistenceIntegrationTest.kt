package com.atruedev.kmpble.observation

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.internal.ObservationKey
import com.atruedev.kmpble.gatt.internal.ObservationManager
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests that ObservationPersistence correctly saves, restores, and clears
 * CCC descriptor states across sessions, and that the ObservationManager
 * callback integration correctly persists observations as they change.
 */
@OptIn(ExperimentalUuidApi::class)
class ObservationPersistenceIntegrationTest {
    private val peripheralId = "00:11:22:33:44:55"
    private val serviceUuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val charUuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
    private val charUuid2 = Uuid.parse("00002a38-0000-1000-8000-00805f9b34fb")

    @Test
    fun saveAndRestorePreservesAllFields() =
        runTest {
            val persistence = ObservationPersistence()
            val observations =
                setOf(
                    PersistedObservation(
                        ObservationKey(serviceUuid, charUuid),
                        BackpressureStrategy.Latest,
                    ),
                    PersistedObservation(
                        ObservationKey(serviceUuid, charUuid2),
                        BackpressureStrategy.Buffer(32),
                    ),
                )

            persistence.save(peripheralId, observations)
            val restored = persistence.restore(peripheralId)

            assertEquals(2, restored.size)
            val byKey = restored.associateBy { it.key }
            assertEquals(BackpressureStrategy.Latest, byKey[ObservationKey(serviceUuid, charUuid)]?.backpressure)
            assertEquals(BackpressureStrategy.Buffer(32), byKey[ObservationKey(serviceUuid, charUuid2)]?.backpressure)
        }

    @Test
    fun restoreReturnsEmptyForUnknownPeripheral() =
        runTest {
            val persistence = ObservationPersistence()
            assertEquals(emptySet(), persistence.restore("unknown:device"))
        }

    @Test
    fun clearRemovesPersistedState() =
        runTest {
            val persistence = ObservationPersistence()
            val observations =
                setOf(
                    PersistedObservation(
                        ObservationKey(serviceUuid, charUuid),
                        BackpressureStrategy.Latest,
                    ),
                )

            persistence.save(peripheralId, observations)
            assertEquals(1, persistence.restore(peripheralId).size)

            persistence.clear(peripheralId)
            assertEquals(emptySet(), persistence.restore(peripheralId))
        }

    @Test
    fun saveEmptySetClearsState() =
        runTest {
            val persistence = ObservationPersistence()
            persistence.save(
                peripheralId,
                setOf(
                    PersistedObservation(
                        ObservationKey(serviceUuid, charUuid),
                        BackpressureStrategy.Latest,
                    ),
                ),
            )
            assertEquals(1, persistence.restore(peripheralId).size)

            persistence.save(peripheralId, emptySet())
            assertEquals(emptySet(), persistence.restore(peripheralId))
        }

    @Test
    fun saveUnboundedBackpressurePreserved() =
        runTest {
            val persistence = ObservationPersistence()
            val observations =
                setOf(
                    PersistedObservation(
                        ObservationKey(serviceUuid, charUuid),
                        BackpressureStrategy.Unbounded,
                    ),
                )

            persistence.save(peripheralId, observations)
            val restored = persistence.restore(peripheralId)

            assertEquals(1, restored.size)
            assertEquals(BackpressureStrategy.Unbounded, restored.first().backpressure)
        }

    @Test
    fun managerCallbackIntegratesWithPersistence() =
        runTest {
            val persistence = ObservationPersistence()
            val manager = ObservationManager(Dispatchers.Unconfined)

            // Wire the callback as AndroidPeripheral does
            manager.onObservationsChanged = { observations ->
                persistence.save(peripheralId, observations)
            }

            // Subscribe - should trigger persistence
            manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)

            // Verify persistence captured the observation
            val restored = persistence.restore(peripheralId)
            assertEquals(1, restored.size)
            assertEquals(
                PersistedObservation(ObservationKey(serviceUuid, charUuid), BackpressureStrategy.Latest),
                restored.first(),
            )

            // Unsubscribe - should clear persistence
            manager.unsubscribe(serviceUuid, charUuid)

            // Verify persistence was cleared (empty set was saved)
            assertEquals(emptySet(), persistence.restore(peripheralId))
        }

    @Test
    fun multiplePeripheralsHaveIndependentState() =
        runTest {
            val persistence = ObservationPersistence()
            val id1 = "AA:BB:CC:DD:EE:FF"
            val id2 = "11:22:33:44:55:66"

            persistence.save(
                id1,
                setOf(
                    PersistedObservation(
                        ObservationKey(serviceUuid, charUuid),
                        BackpressureStrategy.Latest,
                    ),
                ),
            )
            persistence.save(
                id2,
                setOf(
                    PersistedObservation(
                        ObservationKey(serviceUuid, charUuid2),
                        BackpressureStrategy.Buffer(16),
                    ),
                ),
            )

            assertEquals(1, persistence.restore(id1).size)
            assertEquals(1, persistence.restore(id2).size)
            assertEquals(BackpressureStrategy.Latest, persistence.restore(id1).first().backpressure)
            assertEquals(BackpressureStrategy.Buffer(16), persistence.restore(id2).first().backpressure)

            persistence.clear(id1)
            assertEquals(emptySet(), persistence.restore(id1))
            assertEquals(1, persistence.restore(id2).size)
        }
}
