package com.atruedev.kmpble.dfu

import com.atruedev.kmpble.dfu.transport.DfuUuids
import com.atruedev.kmpble.dfu.transport.EspOtaUuids
import com.atruedev.kmpble.dfu.transport.SmpUuids
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class DfuDetectorTest {

    @Test
    fun `returns null when no services discovered`() = runTest {
        val peripheral = FakePeripheral { /* empty */ }
        peripheral.connect()

        // services is null because connect with no services set yields null
        val result = DfuDetector.detect(peripheral)
        assertNull(result)

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `detects Nordic DFU service`() = runTest {
        val peripheral = FakePeripheral {
            service(DfuUuids.DFU_SERVICE) {
                characteristic(DfuUuids.DFU_CONTROL_POINT) {
                    properties(notify = true, write = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
            }
        }

        peripheral.connect()
        val result = DfuDetector.detect(peripheral)
        assertEquals(DfuProtocolType.NORDIC, result)

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `detects MCUboot SMP service`() = runTest {
        val peripheral = FakePeripheral {
            service(SmpUuids.SMP_SERVICE) {
                characteristic(SmpUuids.SMP_CHARACTERISTIC) {
                    properties(notify = true, writeWithoutResponse = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
            }
        }

        peripheral.connect()
        val result = DfuDetector.detect(peripheral)
        assertEquals(DfuProtocolType.MCUBOOT, result)

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `detects ESP OTA service`() = runTest {
        val peripheral = FakePeripheral {
            service(EspOtaUuids.OTA_SERVICE) {
                characteristic(EspOtaUuids.OTA_CONTROL) {
                    properties(notify = true, write = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
            }
        }

        peripheral.connect()
        val result = DfuDetector.detect(peripheral)
        assertEquals(DfuProtocolType.ESP_OTA, result)

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `returns null for unknown service`() = runTest {
        val peripheral = FakePeripheral {
            service("180d") {  // Heart Rate service - not a DFU service
                characteristic("2a37") {
                    properties(notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
            }
        }

        peripheral.connect()
        val result = DfuDetector.detect(peripheral)
        assertNull(result)

        peripheral.disconnect()
        peripheral.close()
    }

    @Test
    fun `Nordic DFU takes priority when multiple DFU services present`() = runTest {
        val peripheral = FakePeripheral {
            service(EspOtaUuids.OTA_SERVICE) {
                characteristic(EspOtaUuids.OTA_CONTROL) {
                    properties(notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
            }
            service(DfuUuids.DFU_SERVICE) {
                characteristic(DfuUuids.DFU_CONTROL_POINT) {
                    properties(notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
            }
            service(SmpUuids.SMP_SERVICE) {
                characteristic(SmpUuids.SMP_CHARACTERISTIC) {
                    properties(notify = true)
                    onWrite { _, _ -> }
                    onObserve { flow {} }
                }
            }
        }

        peripheral.connect()
        // Nordic is checked first (first entry in serviceToProtocol)
        val result = DfuDetector.detect(peripheral)
        assertEquals(DfuProtocolType.NORDIC, result)

        peripheral.disconnect()
        peripheral.close()
    }
}
