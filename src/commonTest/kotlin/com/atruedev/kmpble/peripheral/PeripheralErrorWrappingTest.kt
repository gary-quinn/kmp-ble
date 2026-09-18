package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.error.PeripheralClosed
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.gatt.internal.GattOperationQueue
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.uuid.ExperimentalUuidApi

/**
 * Regression tests for issue #683: public peripheral APIs must surface
 * [BleException] instead of leaking raw platform/internal throwables.
 */
@OptIn(ExperimentalUuidApi::class)
class PeripheralErrorWrappingTest {
    private fun createPeripheral(): FakePeripheral =
        FakePeripheral {
            service("180d") {
                characteristic("2a37") {
                    properties(read = true, write = true)
                    onRead { byteArrayOf(0x01) }
                    onWrite { _, _ -> }
                }
            }
        }

    @Test
    fun closedPeripheralReadThrowsBleExceptionNotIllegalStateException() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()
            peripheral.close()

            val ex =
                assertFailsWith<BleException> {
                    peripheral.read(
                        peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!,
                    )
                }
            assertIs<PeripheralClosed>(ex.error)
        }

    @Test
    fun closedPeripheralWriteThrowsBleExceptionNotIllegalStateException() =
        runTest {
            val peripheral = createPeripheral()
            peripheral.connect()
            peripheral.close()

            val ex =
                assertFailsWith<BleException> {
                    peripheral.write(
                        peripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!,
                        byteArrayOf(0x01),
                        WriteType.WithResponse,
                    )
                }
            assertIs<PeripheralClosed>(ex.error)
        }

    @Test
    fun drainedGattQueueSurfacesBleExceptionNotNotConnectedException() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()
            queue.drain()

            val ex =
                assertFailsWith<BleException> {
                    queue.enqueueBle { "should fail" }
                }
            assertIs<ConnectionLost>(ex.error)
        }

    @Test
    fun platformFailureInsideEnqueueSurfacesBleException() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()

            val ex =
                assertFailsWith<BleException> {
                    queue.enqueueBle { throw RuntimeException("binder died") }
                }
            assertIs<OperationFailed>(ex.error)

            queue.close()
        }

    @Test
    fun consumerCanCatchBleExceptionAloneForClosedAndNotConnectedPaths() =
        runTest {
            val closedPeripheral = createPeripheral()
            closedPeripheral.connect()
            closedPeripheral.close()

            val char = closedPeripheral.findCharacteristic(uuidFrom("180d"), uuidFrom("2a37"))!!

            var caughtClosed = false
            try {
                closedPeripheral.read(char)
            } catch (e: BleException) {
                caughtClosed = e.error is PeripheralClosed
            } catch (_: Exception) {
                // Any other exception type means the leak regressed.
            }
            assert(caughtClosed)

            val queue = GattOperationQueue(this)
            queue.start()
            queue.drain()

            var caughtNotConnected = false
            try {
                queue.enqueueBle { "should fail" }
            } catch (e: BleException) {
                caughtNotConnected = e.error is ConnectionLost
            } catch (_: Exception) {
                // Any other exception type means the leak regressed.
            }
            assert(caughtNotConnected)

            queue.close()
        }
}
