package com.atruedev.kmpble.observation

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.internal.ObservationKey
import com.atruedev.kmpble.gatt.internal.ObservationManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests that ObservationManager fires the onObservationsChanged callback
 * when observations are added or removed. This callback is used by
 * iOS state restoration to persist observation keys to the Keychain.
 */
@OptIn(ExperimentalUuidApi::class)
class ObservationPersistenceCallbackTest {

    private val serviceUuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val charUuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
    private val charUuid2 = Uuid.parse("00002a38-0000-1000-8000-00805f9b34fb")

    @Test
    fun `callback fires on subscribe with correct keys`() = runTest {
        val manager = ObservationManager()
        val receivedKeys = mutableListOf<Set<ObservationKey>>()
        manager.onObservationsChanged = { keys -> receivedKeys.add(keys) }

        manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)

        assertEquals(1, receivedKeys.size)
        assertEquals(
            setOf(ObservationKey(serviceUuid, charUuid)),
            receivedKeys[0],
        )
    }

    @Test
    fun `callback fires on unsubscribe`() = runTest {
        val manager = ObservationManager()
        val receivedKeys = mutableListOf<Set<ObservationKey>>()

        manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)
        manager.onObservationsChanged = { keys -> receivedKeys.add(keys) }

        manager.unsubscribe(serviceUuid, charUuid)

        assertEquals(1, receivedKeys.size)
        assertTrue(receivedKeys[0].isEmpty())
    }

    @Test
    fun `callback includes all active observation keys`() = runTest {
        val manager = ObservationManager()
        val receivedKeys = mutableListOf<Set<ObservationKey>>()
        manager.onObservationsChanged = { keys -> receivedKeys.add(keys) }

        manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)
        manager.subscribe(serviceUuid, charUuid2, BackpressureStrategy.Latest)

        assertEquals(2, receivedKeys.size)
        assertEquals(
            setOf(
                ObservationKey(serviceUuid, charUuid),
                ObservationKey(serviceUuid, charUuid2),
            ),
            receivedKeys[1],
        )
    }

    @Test
    fun `no callback when callback is null`() = runTest {
        val manager = ObservationManager()
        // Should not throw — callback is null by default
        manager.subscribe(serviceUuid, charUuid, BackpressureStrategy.Latest)
        manager.unsubscribe(serviceUuid, charUuid)
    }
}
