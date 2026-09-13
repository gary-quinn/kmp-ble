package com.atruedev.kmpble.peripheral

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

internal open class FakeBlueZDeviceSession(
    override val devicePath: String = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF",
    override val address: String = "AA:BB:CC:DD:EE:FF",
    var connectResult: Result<Unit> = Result.success(Unit),
    var disconnectResult: Result<Unit> = Result.success(Unit),
    var registerHandlersResult: Boolean = true,
    private var connected: Boolean = false,
    private var servicesResolved: Boolean = false,
    private var gattServices: List<BlueZGattServiceSnapshot> = defaultServices(),
    private var rssi: Int? = -55,
) : BlueZDeviceSession {
    var connectCalls: Int = 0
    var disconnectCalls: Int = 0
    var registerHandlersCalls: Int = 0
    var unregisterHandlersCalls: Int = 0
    var refreshGattServicesCalls: Int = 0
    var closeConnectionCalls: Int = 0
    val readCalls: MutableList<String> = mutableListOf()
    val writeCalls: MutableList<Pair<String, ByteArray>> = mutableListOf()
    val notifyCalls: MutableList<String> = mutableListOf()
    val stopNotifyCalls: MutableList<String> = mutableListOf()

    private var deviceHandler: ((Map<String, Any?>) -> Unit)? = null
    private var gattHandler: ((String, Map<String, Any?>) -> Unit)? = null

    private val characteristicValues = mutableMapOf<String, ByteArray>()
    private val descriptorValues = mutableMapOf<String, ByteArray>()

    init {
        rebuildValues()
    }

    fun setGattServices(services: List<BlueZGattServiceSnapshot>) {
        gattServices = services
        rebuildValues()
    }

    fun simulateDisconnected() {
        connected = false
        servicesResolved = false
        deviceHandler?.invoke(mapOf("Connected" to false))
    }

    fun simulateCharacteristicValue(
        charPath: String,
        value: ByteArray,
    ) {
        gattHandler?.invoke(charPath, mapOf("Value" to value))
    }

    suspend fun awaitConnect() {
        withTimeout(2.seconds) {
            while (connectCalls == 0) delay(1)
        }
    }

    suspend fun awaitDisconnect() {
        withTimeout(2.seconds) {
            while (disconnectCalls == 0) delay(1)
        }
    }

    override fun registerHandlers(
        onDevicePropertiesChanged: (changed: Map<String, Any?>) -> Unit,
        onGattPropertiesChanged: (path: String, changed: Map<String, Any?>) -> Unit,
    ): Boolean {
        registerHandlersCalls++
        deviceHandler = onDevicePropertiesChanged
        gattHandler = onGattPropertiesChanged
        return registerHandlersResult
    }

    override fun unregisterHandlers() {
        unregisterHandlersCalls++
        deviceHandler = null
        gattHandler = null
    }

    override fun connect(): Result<Unit> {
        connectCalls++
        return connectResult.onSuccess {
            connected = true
            servicesResolved = true
        }
    }

    override fun disconnect(): Result<Unit> {
        disconnectCalls++
        return disconnectResult.onSuccess {
            connected = false
            servicesResolved = false
        }
    }

    override fun isConnected(): Boolean = connected

    override fun isServicesResolved(): Boolean = servicesResolved

    override fun refreshGattServices() {
        refreshGattServicesCalls++
    }

    override fun getGattServices(): List<BlueZGattServiceSnapshot> = gattServices

    override fun readCharacteristic(path: String): Result<ByteArray> {
        readCalls += path
        val value = characteristicValues[path] ?: return Result.failure(IllegalStateException("unknown characteristic"))
        return Result.success(value.copyOf())
    }

    override fun writeCharacteristic(
        path: String,
        data: ByteArray,
        writeType: String,
    ): Result<Unit> {
        if (path !in characteristicValues) return Result.failure(IllegalStateException("unknown characteristic"))
        writeCalls += path to data.copyOf()
        characteristicValues[path] = data.copyOf()
        return Result.success(Unit)
    }

    override fun readDescriptor(path: String): Result<ByteArray> {
        val value = descriptorValues[path] ?: return Result.failure(IllegalStateException("unknown descriptor"))
        return Result.success(value.copyOf())
    }

    override fun writeDescriptor(
        path: String,
        data: ByteArray,
    ): Result<Unit> {
        if (path !in descriptorValues) return Result.failure(IllegalStateException("unknown descriptor"))
        descriptorValues[path] = data.copyOf()
        return Result.success(Unit)
    }

    override fun startNotify(path: String): Result<Unit> {
        notifyCalls += path
        return Result.success(Unit)
    }

    override fun stopNotify(path: String): Result<Unit> {
        stopNotifyCalls += path
        return Result.success(Unit)
    }

    override fun readRssi(): Int? = rssi

    override fun closeConnection() {
        closeConnectionCalls++
    }

    private fun rebuildValues() {
        characteristicValues.clear()
        descriptorValues.clear()
        for (service in gattServices) {
            for (char in service.characteristics) {
                characteristicValues[char.path] = byteArrayOf(0x4B)
                for (desc in char.descriptors) {
                    descriptorValues[desc.path] = byteArrayOf(0x00, 0x00)
                }
            }
        }
    }

    companion object {
        fun defaultServices(): List<BlueZGattServiceSnapshot> =
            listOf(
                BlueZGattServiceSnapshot(
                    path = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF/service0001",
                    uuid = "0000180d-0000-1000-8000-00805f9b34fb",
                    characteristics =
                        listOf(
                            BlueZGattCharacteristicSnapshot(
                                path = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF/service0001/char0001",
                                uuid = "00002a37-0000-1000-8000-00805f9b34fb",
                                flags = listOf("read", "notify"),
                                descriptors = emptyList(),
                            ),
                        ),
                ),
            )
    }
}

internal class FakeBlueZDeviceSessionFactory(
    private val session: FakeBlueZDeviceSession,
) : BlueZDeviceSessionFactory {
    var openCalls: Int = 0

    override fun open(
        devicePath: String,
        address: String,
    ): BlueZDeviceSessionOpenResult {
        openCalls++
        return BlueZDeviceSessionOpenResult.Ready(session)
    }
}
