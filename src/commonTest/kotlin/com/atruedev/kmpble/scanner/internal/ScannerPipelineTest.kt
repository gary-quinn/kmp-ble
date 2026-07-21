package com.atruedev.kmpble.scanner.internal

import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.ScanFailedException
import com.atruedev.kmpble.scanner.ScannerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
}
