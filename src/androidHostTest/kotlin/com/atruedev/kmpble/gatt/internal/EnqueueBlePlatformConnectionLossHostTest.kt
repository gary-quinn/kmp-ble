package com.atruedev.kmpble.gatt.internal

import android.os.DeadObjectException
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.ConnectionFailureReason
import com.atruedev.kmpble.error.ConnectionLost
import com.atruedev.kmpble.error.OperationFailed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Regression tests for issue #686: platform connection-loss throwables (e.g.
 * [DeadObjectException]) must map to [ConnectionLost], not [OperationFailed].
 */
class EnqueueBlePlatformConnectionLossHostTest {
    @Test
    fun deadObjectExceptionInsideEnqueueBleSurfacesAsConnectionLost() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()

            val ex =
                assertFailsWith<BleException> {
                    queue.enqueueBle { throw DeadObjectException() }
                }
            val error = assertIs<ConnectionLost>(ex.error)
            assertEquals(ConnectionFailureReason.LINK_LOSS, error.failureReason)

            queue.close()
        }

    @Test
    fun unrecognizedThrowableInsideEnqueueBleStillSurfacesAsOperationFailed() =
        runTest {
            val queue = GattOperationQueue(this)
            queue.start()

            val ex =
                assertFailsWith<BleException> {
                    queue.enqueueBle { throw RuntimeException("unexpected") }
                }
            assertIs<OperationFailed>(ex.error)

            queue.close()
        }
}
