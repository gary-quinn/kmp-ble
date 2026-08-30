package com.atruedev.kmpble.peripheral

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattError
import com.atruedev.kmpble.error.OperationFailed
import com.atruedev.kmpble.gatt.Characteristic
import com.atruedev.kmpble.quirks.QuirkRegistry
import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

/**
 * Instrumented integration tests for [AndroidPeripheral.writeReliableGatt] against
 * the Android reliable-write callback sequence (begin / per-chunk write ack /
 * execute / onReliableWriteCompleted, plus abort on failure or cancellation).
 *
 * Uses [RecordingAndroidGattBridge] to drive [AndroidPeripheral.handleGattEvent]
 * without a live Bluetooth link. Runs on the android-instrumented CI job.
 */
@OptIn(ExperimentalUuidApi::class)
@RunWith(AndroidJUnit4::class)
class WriteReliableIntegrationTest {
    private lateinit var peripheral: AndroidPeripheral
    private lateinit var recordingBridge: RecordingAndroidGattBridge
    private lateinit var kmpChar: Characteristic
    private lateinit var nativeChar: BluetoothGattCharacteristic

    private val serviceUuid = uuidFrom("180d")
    private val charUuid = uuidFrom("2a37")

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val adapter = BluetoothAdapter.getDefaultAdapter()
        assumeTrue("Bluetooth adapter required", adapter != null)
        val device = adapter!!.getRemoteDevice("00:11:22:33:44:55")

        recordingBridge = RecordingAndroidGattBridge(device, context)
        peripheral =
            AndroidPeripheral(
                device,
                context,
                QuirkRegistry.getInstance(),
                recordingBridge,
            )
        recordingBridge.peripheral = peripheral

        kmpChar =
            Characteristic(
                serviceUuid = serviceUuid,
                uuid = charUuid,
                properties = Characteristic.Properties(write = true),
            )
        nativeChar =
            BluetoothGattCharacteristic(
                charUuid.toJavaUuid(),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        val nativeService =
            BluetoothGattService(
                serviceUuid.toJavaUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY,
            )
        nativeService.addCharacteristic(nativeChar)

        peripheral.peripheralContext.gattQueue.start()
        peripheral.nativeCharMap[kmpChar] = nativeChar
    }

    @After
    fun teardown() {
        peripheral.close()
    }

    @Test
    fun multiChunk_happyPath_firesPerChunkWriteAndReliableWriteCompleted() =
        runBlocking {
            val data = ByteArray(25) { it.toByte() }

            withTimeout(5.seconds) {
                peripheral.writeReliableGatt(kmpChar, data)
            }

            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.BeginReliableWrite })
            assertEquals(2, recordingBridge.calls.count { it is ReliableWriteCall.WriteCharacteristic })
            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.ExecuteReliableWrite })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.AbortReliableWrite })
        }

    @Test
    fun singleChunk_passthroughWithoutReliableWriteTransaction() =
        runBlocking {
            val data = byteArrayOf(0x01, 0x02, 0x03)

            withTimeout(5.seconds) {
                peripheral.writeReliableGatt(kmpChar, data)
            }

            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.BeginReliableWrite })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.ExecuteReliableWrite })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.AbortReliableWrite })
            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.WriteCharacteristic })
        }

    @Test
    fun chunkFailure_midSession_abortsWithoutCommit() =
        runBlocking {
            recordingBridge.writeCharacteristicStatus = BluetoothGatt.GATT_FAILURE
            val data = ByteArray(25) { it.toByte() }

            val error =
                assertFailsWith<BleException> {
                    withTimeout(5.seconds) {
                        peripheral.writeReliableGatt(kmpChar, data)
                    }
                }
            assertTrue(error.error is GattError)

            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.BeginReliableWrite })
            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.WriteCharacteristic })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.ExecuteReliableWrite })
            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.AbortReliableWrite })
        }

    @Test
    fun executeReliableWriteInitiationFailure_returnsOperationFailed() =
        runBlocking {
            recordingBridge.executeReliableWriteResult = false
            val data = ByteArray(25) { it.toByte() }

            val error =
                assertFailsWith<BleException> {
                    withTimeout(5.seconds) {
                        peripheral.writeReliableGatt(kmpChar, data)
                    }
                }
            assertTrue(error.error is OperationFailed)

            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.BeginReliableWrite })
            assertEquals(2, recordingBridge.calls.count { it is ReliableWriteCall.WriteCharacteristic })
            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.ExecuteReliableWrite })
            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.AbortReliableWrite })
        }

    @Test
    fun beginReliableWriteInitiationFailure_returnsOperationFailed() =
        runBlocking {
            recordingBridge.beginReliableWriteResult = false
            val data = ByteArray(25) { it.toByte() }

            val error =
                assertFailsWith<BleException> {
                    withTimeout(5.seconds) {
                        peripheral.writeReliableGatt(kmpChar, data)
                    }
                }
            assertTrue(error.error is OperationFailed)

            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.BeginReliableWrite })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.WriteCharacteristic })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.ExecuteReliableWrite })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.AbortReliableWrite })
        }

    @Test
    fun reliableWriteCompletedGattFailure_abortsTransaction() =
        runBlocking {
            recordingBridge.reliableWriteCompletedStatus = BluetoothGatt.GATT_FAILURE
            val data = ByteArray(25) { it.toByte() }

            val error =
                assertFailsWith<BleException> {
                    withTimeout(5.seconds) {
                        peripheral.writeReliableGatt(kmpChar, data)
                    }
                }
            assertTrue(error.error is GattError)

            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.ExecuteReliableWrite })
            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.AbortReliableWrite })
        }

    @Test
    fun callerCancellation_midSession_abortsReliableWrite() =
        runBlocking {
            recordingBridge.autoCompleteFirstWriteOnly = true
            val data = ByteArray(25) { it.toByte() }

            var job: Job? = null
            var observedCancellation = false
            job =
                launch(peripheral.peripheralContext.scope.coroutineContext) {
                    try {
                        peripheral.writeReliableGatt(kmpChar, data)
                    } catch (e: CancellationException) {
                        observedCancellation = true
                        throw e
                    }
                }

            withTimeout(5.seconds) {
                while (recordingBridge.calls.count { it is ReliableWriteCall.WriteCharacteristic } < 1) {
                    delay(10)
                }
                job.cancel()
                job.join()
            }

            assertTrue(observedCancellation, "Caller must observe CancellationException")
            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.AbortReliableWrite })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.ExecuteReliableWrite })
        }

    @Test
    fun queueUsableAfterCancelledReliableWriteTransaction() =
        runBlocking {
            recordingBridge.autoCompleteFirstWriteOnly = true
            val multiChunk = ByteArray(25) { it.toByte() }

            val cancelJob =
                launch(peripheral.peripheralContext.scope.coroutineContext) {
                    try {
                        peripheral.writeReliableGatt(kmpChar, multiChunk)
                    } catch (_: CancellationException) {
                    }
                }

            withTimeout(5.seconds) {
                while (recordingBridge.calls.count { it is ReliableWriteCall.WriteCharacteristic } < 1) {
                    delay(10)
                }
                cancelJob.cancel()
                cancelJob.join()
            }

            recordingBridge.resetRecording()
            recordingBridge.autoCompleteFirstWriteOnly = false
            recordingBridge.autoCompleteWrites = true

            withTimeout(5.seconds) {
                peripheral.writeReliableGatt(kmpChar, byteArrayOf(0x01))
            }

            assertEquals(1, recordingBridge.calls.count { it is ReliableWriteCall.WriteCharacteristic })
            assertFalse(recordingBridge.calls.any { it is ReliableWriteCall.AbortReliableWrite })
        }
}
