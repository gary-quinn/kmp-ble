package com.atruedev.kmpble.bluez

import org.bluez.exceptions.BluezFailedException
import org.bluez.exceptions.BluezNotConnectedException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

internal open class FakeBlueZDeviceSession(
    override val devicePath: String = DEVICE_PATH,
    override val address: String = "AA:BB:CC:DD:EE:FF",
    var connectFailure: Exception? = null,
    var registerResult: Boolean = true,
    private var services: List<BlueZGattServiceSnapshot> = defaultServices(),
    var rssiValue: Int? = -55,
) : BlueZDeviceSession {
    @Volatile var signals: BlueZDeviceSignals? = null

    @Volatile var connected = false

    @Volatile var resolved = false

    @Volatile var paired = false

    var connectCalls = 0
    var registerCalls = 0
    var echoReads = false
    var disconnectCalls = 0
    var pairCalls = 0
    var cancelPairingCalls = 0
    var removeCalls = 0
    var closeCalls = 0
    var connectBlock: CountDownLatch? = null
    var readFailure: Exception? = null
    val reads = CopyOnWriteArrayList<String>()
    val writes = CopyOnWriteArrayList<Triple<String, ByteArray, String>>()
    val descriptorWrites = CopyOnWriteArrayList<Pair<String, ByteArray>>()
    val notifyStarts = CopyOnWriteArrayList<String>()
    val notifyStops = CopyOnWriteArrayList<String>()

    fun setServices(value: List<BlueZGattServiceSnapshot>) {
        services = value
    }

    fun simulateDisconnected() {
        connected = false
        resolved = false
        signals?.onDeviceChanged(mapOf("Connected" to false))
    }

    fun simulateValue(
        path: String,
        value: ByteArray,
    ) {
        signals?.onAttributeChanged(path, mapOf("Value" to value))
    }

    override fun registerSignals(signals: BlueZDeviceSignals): Boolean {
        registerCalls++
        this.signals = signals
        return registerResult
    }

    override fun unregisterSignals() {
        signals = null
    }

    override fun connect(): BlueZPendingCall {
        connectCalls++
        val gate = connectBlock
        return object : BlueZPendingCall {
            override fun isDone(): Boolean = gate == null || gate.count == 0L

            override fun result() {
                connectFailure?.let { throw it }
                connected = true
                resolved = true
            }
        }
    }

    override fun disconnect() {
        disconnectCalls++
        connectBlock?.countDown()
        if (!connected) throw BluezNotConnectedException("not connected")
        connected = false
        resolved = false
        signals?.onDeviceChanged(mapOf("Connected" to false))
    }

    override fun isConnected(): Boolean = connected

    override fun isServicesResolved(): Boolean = resolved

    override fun gattServices(): List<BlueZGattServiceSnapshot> = services

    override fun readCharacteristic(path: String): ByteArray {
        requireConnected()
        readFailure?.let { throw it }
        reads += path
        if (echoReads) signals?.onAttributeChanged(path, mapOf("Value" to byteArrayOf(0x4B)))
        return byteArrayOf(0x4B)
    }

    override fun writeCharacteristic(
        path: String,
        data: ByteArray,
        type: String,
    ) {
        requireConnected()
        writes += Triple(path, data, type)
    }

    override fun readDescriptor(path: String): ByteArray {
        requireConnected()
        return byteArrayOf(0x00, 0x00)
    }

    override fun writeDescriptor(
        path: String,
        data: ByteArray,
    ) {
        requireConnected()
        descriptorWrites += path to data
    }

    override fun startNotify(path: String) {
        requireConnected()
        notifyStarts += path
    }

    override fun stopNotify(path: String) {
        notifyStops += path
    }

    override fun rssi(): Int? = rssiValue

    override fun isPaired(): Boolean = paired

    override fun isBonded(): Boolean? = null

    override fun pair(): BlueZPendingCall {
        pairCalls++
        return object : BlueZPendingCall {
            override fun isDone(): Boolean = true

            override fun result() {
                paired = true
            }
        }
    }

    override fun cancelPairing() {
        cancelPairingCalls++
    }

    override fun removeFromAdapter() {
        removeCalls++
        paired = false
    }

    override fun closeConnection() {
        closeCalls++
    }

    private fun requireConnected() {
        if (!connected) throw BluezNotConnectedException("Not connected")
    }

    companion object {
        const val DEVICE_PATH = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF"
        const val SERVICE_PATH = "$DEVICE_PATH/service0010"
        const val CHAR_PATH = "$SERVICE_PATH/char0011"
        const val CCCD_PATH = "$CHAR_PATH/desc0013"
        const val WRITE_PATH = "$SERVICE_PATH/char0014"

        fun defaultServices(): List<BlueZGattServiceSnapshot> =
            listOf(
                BlueZGattServiceSnapshot(
                    path = SERVICE_PATH,
                    uuid = "0000180d-0000-1000-8000-00805f9b34fb",
                    characteristics =
                        listOf(
                            BlueZGattCharacteristicSnapshot(
                                path = CHAR_PATH,
                                uuid = "00002a37-0000-1000-8000-00805f9b34fb",
                                flags = listOf("read", "notify"),
                                descriptors =
                                    listOf(
                                        BlueZGattDescriptorSnapshot(CCCD_PATH, "00002902-0000-1000-8000-00805f9b34fb"),
                                    ),
                                mtu = 247,
                            ),
                            BlueZGattCharacteristicSnapshot(
                                path = WRITE_PATH,
                                uuid = "00002a39-0000-1000-8000-00805f9b34fb",
                                flags =
                                    listOf(
                                        "write",
                                        "write-without-response",
                                        "reliable-write",
                                        "authenticated-signed-writes",
                                    ),
                                descriptors = emptyList(),
                                mtu = 247,
                            ),
                        ),
                ),
            )

        fun failed(message: String): BluezFailedException = BluezFailedException(message)
    }
}

internal class FakeBlueZDeviceSessionFactory(
    private val session: FakeBlueZDeviceSession,
) : BlueZDeviceSessionFactory {
    var openCalls = 0
    val requestedPaths = CopyOnWriteArrayList<String?>()

    override fun open(
        devicePath: String?,
        address: String,
    ): BlueZDeviceSessionOpenResult {
        openCalls++
        requestedPaths += devicePath
        return BlueZDeviceSessionOpenResult.Ready(session)
    }
}
