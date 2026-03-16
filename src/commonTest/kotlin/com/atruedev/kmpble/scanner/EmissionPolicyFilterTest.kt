package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.internal.applyEmissionPolicy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class EmissionPolicyFilterTest {

    private fun ad(
        identifier: String = "device-1",
        name: String? = "Sensor",
        rssi: Int = -60,
    ) = Advertisement(
        identifier = Identifier(identifier),
        name = name,
        rssi = rssi,
        txPower = null,
        isConnectable = true,
        serviceUuids = emptyList(),
        manufacturerData = emptyMap(),
        serviceData = emptyMap(),
        timestampNanos = 0L,
    )

    @Test
    fun allPolicyEmitsEverything() = runTest {
        val ads = listOf(ad(), ad(), ad())
        val result = flowOf(*ads.toTypedArray())
            .applyEmissionPolicy(EmissionPolicy.All)
            .toList()
        assertEquals(3, result.size)
    }

    @Test
    fun firstThenChangesEmitsFirstPerDevice() = runTest {
        val result = flowOf(
            ad(identifier = "device-1"),
            ad(identifier = "device-2"),
            ad(identifier = "device-1"), // duplicate, same data
        )
            .applyEmissionPolicy(EmissionPolicy.FirstThenChanges())
            .toList()

        assertEquals(2, result.size)
        assertEquals("device-1", result[0].identifier.value)
        assertEquals("device-2", result[1].identifier.value)
    }

    @Test
    fun firstThenChangesReEmitsOnRssiChange() = runTest {
        val result = flowOf(
            ad(identifier = "device-1", rssi = -60),
            ad(identifier = "device-1", rssi = -62), // within threshold (5)
            ad(identifier = "device-1", rssi = -66), // exceeds threshold from -60
        )
            .applyEmissionPolicy(EmissionPolicy.FirstThenChanges(rssiThreshold = 5))
            .toList()

        assertEquals(2, result.size)
        assertEquals(-60, result[0].rssi)
        assertEquals(-66, result[1].rssi)
    }

    @Test
    fun firstThenChangesReEmitsOnNameChange() = runTest {
        val result = flowOf(
            ad(identifier = "device-1", name = "Sensor"),
            ad(identifier = "device-1", name = "Sensor"), // same
            ad(identifier = "device-1", name = "Sensor-v2"), // name changed
        )
            .applyEmissionPolicy(EmissionPolicy.FirstThenChanges())
            .toList()

        assertEquals(2, result.size)
        assertEquals("Sensor", result[0].name)
        assertEquals("Sensor-v2", result[1].name)
    }

    @Test
    fun firstThenChangesReEmitsOnServiceUuidChange() = runTest {
        val uuid1 = uuidFrom("180d")
        val uuid2 = uuidFrom("180a")

        val result = flowOf(
            Advertisement(
                identifier = Identifier("device-1"),
                name = null, rssi = -60, txPower = null, isConnectable = true,
                serviceUuids = listOf(uuid1),
                manufacturerData = emptyMap(), serviceData = emptyMap(), timestampNanos = 0L,
            ),
            Advertisement(
                identifier = Identifier("device-1"),
                name = null, rssi = -60, txPower = null, isConnectable = true,
                serviceUuids = listOf(uuid1, uuid2), // added a UUID
                manufacturerData = emptyMap(), serviceData = emptyMap(), timestampNanos = 0L,
            ),
        )
            .applyEmissionPolicy(EmissionPolicy.FirstThenChanges())
            .toList()

        assertEquals(2, result.size)
    }

    @Test
    fun customRssiThreshold() = runTest {
        val result = flowOf(
            ad(identifier = "device-1", rssi = -60),
            ad(identifier = "device-1", rssi = -69), // within threshold of 10
            ad(identifier = "device-1", rssi = -71), // exceeds threshold from -60
        )
            .applyEmissionPolicy(EmissionPolicy.FirstThenChanges(rssiThreshold = 10))
            .toList()

        assertEquals(2, result.size)
        assertEquals(-60, result[0].rssi)
        assertEquals(-71, result[1].rssi)
    }
}
