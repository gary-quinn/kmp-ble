package com.atruedev.kmpble.testing

import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class FakeScannerTest {

    @Test
    fun emitsPreConfiguredAdvertisements() = runTest {
        val scanner = FakeScanner {
            advertisement {
                name("HeartSensor")
                rssi(-55)
                serviceUuids("180d")
            }
            advertisement {
                name("TempSensor")
                rssi(-70)
                serviceUuids("1809")
            }
        }

        val results = scanner.advertisements.take(2).toList()
        assertEquals(2, results.size)
        assertEquals("HeartSensor", results[0].name)
        assertEquals(-55, results[0].rssi)
        assertEquals(uuidFrom("180d"), results[0].serviceUuids.first())
        assertEquals("TempSensor", results[1].name)
    }

    @Test
    fun emptyScanner() = runTest {
        val scanner = FakeScanner {}
        // No pre-configured ads — flow suspends waiting for dynamic emissions
        scanner.close()
    }

    @Test
    fun advertisementDefaults() = runTest {
        val scanner = FakeScanner {
            advertisement {}
        }

        val ad = scanner.advertisements.take(1).toList().first()
        assertNull(ad.name)
        assertEquals(-60, ad.rssi)
        assertEquals(true, ad.isConnectable)
        assertEquals(emptyList(), ad.serviceUuids)
    }

    @Test
    fun multipleServiceUuids() = runTest {
        val scanner = FakeScanner {
            advertisement {
                serviceUuids("180d", "180a", "180f")
            }
        }

        val ad = scanner.advertisements.take(1).toList().first()
        assertEquals(3, ad.serviceUuids.size)
        assertEquals(uuidFrom("180d"), ad.serviceUuids[0])
        assertEquals(uuidFrom("180a"), ad.serviceUuids[1])
        assertEquals(uuidFrom("180f"), ad.serviceUuids[2])
    }

    @Test
    fun customIdentifier() = runTest {
        val scanner = FakeScanner {
            advertisement {
                identifier("AA:BB:CC:DD:EE:FF")
                name("Custom")
            }
        }

        val ad = scanner.advertisements.take(1).toList().first()
        assertEquals("AA:BB:CC:DD:EE:FF", ad.identifier.value)
    }
}
