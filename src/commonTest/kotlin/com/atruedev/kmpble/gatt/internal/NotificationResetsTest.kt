package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.scanner.uuidFrom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class NotificationResetsTest {
    private val first = ObservationKey(uuidFrom("180d"), uuidFrom("2a37"))
    private val second = ObservationKey(uuidFrom("180d"), uuidFrom("2a39"))

    private suspend fun NotificationResets.enableAndReport(
        key: ObservationKey,
        requested: Boolean = true,
    ): Boolean {
        var reset: Boolean? = null
        enable(key, requested) { reset = it }
        return checkNotNull(reset)
    }

    @Test
    fun resetsOnlyTheFirstEnablePerCharacteristic() =
        runTest {
            val resets = NotificationResets()

            assertEquals(true, resets.enableAndReport(first))
            assertEquals(false, resets.enableAndReport(first))
            assertEquals(true, resets.enableAndReport(second))
        }

    @Test
    fun clearMakesTheNextEnableResetAgain() =
        runTest {
            val resets = NotificationResets()
            resets.enableAndReport(first)

            resets.clear()

            assertEquals(true, resets.enableAndReport(first))
        }

    @Test
    fun neverResetsWhenNotRequested() =
        runTest {
            val resets = NotificationResets()

            assertEquals(false, resets.enableAndReport(first, requested = false))
            assertEquals(true, resets.enableAndReport(first))
        }

    @Test
    fun failedResetIsRetriedByTheNextEnable() =
        runTest {
            val resets = NotificationResets()

            assertFailsWith<IllegalStateException> {
                resets.enable(first, requested = true) { error("write failed") }
            }

            assertEquals(true, resets.enableAndReport(first))
        }
}
