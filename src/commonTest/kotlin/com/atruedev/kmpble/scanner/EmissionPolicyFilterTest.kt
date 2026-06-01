package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.internal.applyEmissionPolicy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
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

    private fun found(ad: Advertisement) = ScanEvent.Found(ad)

    @Test
    fun allPolicyEmitsEverything() =
        runTest {
            val ads = listOf(ad(), ad(), ad())
            val result =
                flowOf(*ads.map { found(it) }.toTypedArray())
                    .applyEmissionPolicy(EmissionPolicy.All)
                    .toList()
            assertEquals(3, result.size)
        }

    @Test
    fun firstThenChangesEmitsFirstPerDevice() =
        runTest {
            val result =
                flowOf(
                    found(ad(identifier = "device-1")),
                    found(ad(identifier = "device-2")),
                    // duplicate, same data
                    found(ad(identifier = "device-1")),
                ).applyEmissionPolicy(EmissionPolicy.FirstThenChanges())
                    .mapNotNull { (it as? ScanEvent.Found)?.advertisement }
                    .toList()

            assertEquals(2, result.size)
            assertEquals("device-1", result[0].identifier.value)
            assertEquals("device-2", result[1].identifier.value)
        }

    @Test
    fun firstThenChangesReEmitsOnRssiChange() =
        runTest {
            val result =
                flowOf(
                    found(ad(identifier = "device-1", rssi = -60)),
                    // within threshold (5)
                    found(ad(identifier = "device-1", rssi = -62)),
                    // exceeds threshold from -60
                    found(ad(identifier = "device-1", rssi = -66)),
                ).applyEmissionPolicy(EmissionPolicy.FirstThenChanges(rssiThreshold = 5))
                    .mapNotNull { (it as? ScanEvent.Found)?.advertisement }
                    .toList()

            assertEquals(2, result.size)
            assertEquals(-60, result[0].rssi)
            assertEquals(-66, result[1].rssi)
        }

    @Test
    fun firstThenChangesReEmitsOnNameChange() =
        runTest {
            val result =
                flowOf(
                    found(ad(identifier = "device-1", name = "Sensor")),
                    // same
                    found(ad(identifier = "device-1", name = "Sensor")),
                    // name changed
                    found(ad(identifier = "device-1", name = "Sensor-v2")),
                ).applyEmissionPolicy(EmissionPolicy.FirstThenChanges())
                    .mapNotNull { (it as? ScanEvent.Found)?.advertisement }
                    .toList()

            assertEquals(2, result.size)
            assertEquals("Sensor", result[0].name)
            assertEquals("Sensor-v2", result[1].name)
        }

    @Test
    fun firstThenChangesReEmitsOnServiceUuidChange() =
        runTest {
            val uuid1 = uuidFrom("180d")
            val uuid2 = uuidFrom("180a")

            val result =
                flowOf(
                    found(
                        Advertisement(
                            identifier = Identifier("device-1"),
                            name = null,
                            rssi = -60,
                            txPower = null,
                            isConnectable = true,
                            serviceUuids = listOf(uuid1),
                            manufacturerData = emptyMap(),
                            serviceData = emptyMap(),
                            timestampNanos = 0L,
                        ),
                    ),
                    found(
                        Advertisement(
                            identifier = Identifier("device-1"),
                            name = null,
                            rssi = -60,
                            txPower = null,
                            isConnectable = true,
                            // added a UUID
                            serviceUuids = listOf(uuid1, uuid2),
                            manufacturerData = emptyMap(),
                            serviceData = emptyMap(),
                            timestampNanos = 0L,
                        ),
                    ),
                ).applyEmissionPolicy(EmissionPolicy.FirstThenChanges())
                    .mapNotNull { (it as? ScanEvent.Found)?.advertisement }
                    .toList()

            assertEquals(2, result.size)
        }

    @Test
    fun customRssiThreshold() =
        runTest {
            val result =
                flowOf(
                    found(ad(identifier = "device-1", rssi = -60)),
                    // within threshold of 10
                    found(ad(identifier = "device-1", rssi = -69)),
                    // exceeds threshold from -60
                    found(ad(identifier = "device-1", rssi = -71)),
                ).applyEmissionPolicy(EmissionPolicy.FirstThenChanges(rssiThreshold = 10))
                    .mapNotNull { (it as? ScanEvent.Found)?.advertisement }
                    .toList()

            assertEquals(2, result.size)
            assertEquals(-60, result[0].rssi)
            assertEquals(-71, result[1].rssi)
        }

    @Test
    fun failedEventsPassThroughUnchanged() =
        runTest {
            val error = ScanFailedException(2)
            val result =
                flowOf(
                    ScanEvent.Failed(error),
                    found(ad()),
                    ScanEvent.Failed(error),
                ).applyEmissionPolicy(EmissionPolicy.FirstThenChanges())
                    .toList()

            assertEquals(3, result.size)
            assertEquals(ScanEvent.Failed(error), result[0])
            assertEquals(ScanEvent.Failed(error), result[2])
        }

    @Test
    fun failedEventsPassThroughAllPolicy() =
        runTest {
            val error = ScanFailedException(1)
            val result =
                flowOf(ScanEvent.Failed(error))
                    .applyEmissionPolicy(EmissionPolicy.All)
                    .toList()

            assertEquals(1, result.size)
            assertEquals(ScanEvent.Failed(error), result.first())
        }
}
