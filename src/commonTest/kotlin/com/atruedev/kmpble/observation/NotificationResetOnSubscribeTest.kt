@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.atruedev.kmpble.observation

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.scanner.uuidFrom
import com.atruedev.kmpble.testing.FakePeripheral
import com.atruedev.kmpble.testing.FakePeripheral.CccdWrite
import com.atruedev.kmpble.testing.FakePeripheralBuilder
import com.atruedev.kmpble.testing.clearCccdWrites
import com.atruedev.kmpble.testing.getCccdWrites
import com.atruedev.kmpble.testing.simulateDisconnect
import com.atruedev.kmpble.testing.simulateReconnect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class NotificationResetOnSubscribeTest {
    private val serviceUuid = uuidFrom("180d")
    private val firstUuid = uuidFrom("2a37")
    private val secondUuid = uuidFrom("2a39")
    private val resetOptions = ConnectionOptions(resetNotificationsOnSubscribe = true)

    private fun createPeripheral(dispatcher: CoroutineDispatcher): FakePeripheral =
        FakePeripheralBuilder()
            .observationDispatcher(dispatcher)
            .apply {
                service("180d") {
                    characteristic("2a37") { properties(notify = true) }
                    characteristic("2a39") { properties(notify = true) }
                }
            }.build()

    private fun TestScope.observe(
        peripheral: FakePeripheral,
        charUuid: Uuid,
    ) = launch {
        val characteristic = peripheral.findCharacteristic(serviceUuid, charUuid)!!
        peripheral.observe(characteristic, BackpressureStrategy.Unbounded).collect { }
    }.also { runCurrent() }

    private fun disable(charUuid: Uuid) = CccdWrite(serviceUuid, charUuid, enabled = false)

    private fun enable(charUuid: Uuid) = CccdWrite(serviceUuid, charUuid, enabled = true)

    @Test
    fun firstCollectorOfEachCharacteristicResetsOnce() =
        runTest {
            val peripheral = createPeripheral(StandardTestDispatcher(testScheduler))
            peripheral.connect(resetOptions)

            val first = observe(peripheral, firstUuid)
            val second = observe(peripheral, firstUuid)
            val third = observe(peripheral, secondUuid)

            assertEquals(
                listOf(
                    disable(firstUuid),
                    enable(firstUuid),
                    enable(firstUuid),
                    disable(secondUuid),
                    enable(secondUuid),
                ),
                peripheral.getCccdWrites(),
            )
            listOf(first, second, third).forEach { it.cancelAndJoin() }
            peripheral.close()
        }

    @Test
    fun resubscribeAfterReconnectResetsAgain() =
        runTest {
            val peripheral = createPeripheral(StandardTestDispatcher(testScheduler))
            peripheral.connect(resetOptions)
            val collector = observe(peripheral, firstUuid)
            peripheral.clearCccdWrites()

            peripheral.simulateDisconnect()
            peripheral.simulateReconnect()
            runCurrent()

            assertEquals(listOf(disable(firstUuid), enable(firstUuid)), peripheral.getCccdWrites())
            collector.cancelAndJoin()
            peripheral.close()
        }

    @Test
    fun newCollectorAfterDisconnectAndConnectResetsAgain() =
        runTest {
            val peripheral = createPeripheral(StandardTestDispatcher(testScheduler))
            peripheral.connect(resetOptions)
            observe(peripheral, firstUuid).cancelAndJoin()
            peripheral.disconnect()
            peripheral.connect(resetOptions)
            peripheral.clearCccdWrites()

            val collector = observe(peripheral, firstUuid)

            assertEquals(listOf(disable(firstUuid), enable(firstUuid)), peripheral.getCccdWrites())
            collector.cancelAndJoin()
            peripheral.close()
        }

    @Test
    fun plainEnableByDefault() =
        runTest {
            val peripheral = createPeripheral(StandardTestDispatcher(testScheduler))
            peripheral.connect()

            val collector = observe(peripheral, firstUuid)

            assertEquals(listOf(enable(firstUuid)), peripheral.getCccdWrites())
            collector.cancelAndJoin()
            peripheral.close()
        }
}
