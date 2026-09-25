package com.atruedev.kmpble.scanner.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.ScanFailedException
import com.atruedev.kmpble.scanner.ScannerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ScannerPipelineTest {
    @Test
    fun `callbackFlow closed with ScanFailedException becomes Failed event`() =
        runTest {
            val errorCode = -1
            val errorMessage = "BluetoothLeScanner not available. Is Bluetooth enabled?"

            // Simulate the AndroidScanner.createRawScanFlow() path when
            // BluetoothLeScanner is null: the callbackFlow calls close() with
            // a ScanFailedException and returns without registering awaitClose.
            // The catch block in toScanEvents must convert this to a
            // ScanEvent.Failed instead of crashing the scope (#590).
            val rawFlow =
                callbackFlow<Advertisement> {
                    close(ScanFailedException(errorCode, errorMessage))
                    // No awaitClose -- matches createRawScanFlow's early return
                }

            val config = ScannerConfig()
            val scope = CoroutineScope(SupervisorJob())

            val shared = rawFlow.toScanEvents(config, scope)
            val deferred = async { shared.first() }
            val event = deferred.await()

            assertIs<ScanEvent.Failed>(event)
            assertEquals(errorCode, event.error.errorCode)
            assertTrue(
                event.error.message!!.contains("BluetoothLeScanner"),
                "expected message to contain 'BluetoothLeScanner', was: ${event.error.message}",
            )
            scope.cancel()
        }

    @Test
    fun `collection completes after a Failed event`() =
        runTest {
            val rawFlow =
                flow {
                    emit(advertisement("A"))
                    throw ScanFailedException(7, "adapter gone")
                }

            val events = rawFlow.toScanEvents(ScannerConfig(), backgroundScope).toList()

            assertEquals(2, events.size)
            assertIs<ScanEvent.Found>(events[0])
            assertEquals(7, assertIs<ScanEvent.Failed>(events[1]).error.errorCode)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `timeout ends a collection even when nothing is found`() =
        runTest {
            val silent = flow<Advertisement> { awaitCancellation() }
            val config = ScannerConfig().apply { timeout = 3.seconds }
            val events = silent.toScanEvents(config, backgroundScope)

            val start = currentTime
            assertTrue(events.toList().isEmpty())
            assertEquals(3_000, currentTime - start)

            val again = currentTime
            events.toList()
            assertEquals(3_000, currentTime - again)
        }

    private fun advertisement(name: String) =
        Advertisement(
            identifier = Identifier(name),
            name = name,
            rssi = -50,
            txPower = null,
            isConnectable = true,
            serviceUuids = emptyList(),
            manufacturerData = emptyMap(),
            serviceData = emptyMap(),
            timestampNanos = 0,
        )
}
