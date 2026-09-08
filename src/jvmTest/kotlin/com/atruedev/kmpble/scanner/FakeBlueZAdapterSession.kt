package com.atruedev.kmpble.scanner

import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.freedesktop.dbus.types.Variant
import kotlin.time.Duration.Companion.seconds

internal open class FakeBlueZAdapterSession(
    override val adapterPath: String = "/org/bluez/hci0",
    var setDiscoveryFilterResult: Result<Unit> = Result.success(Unit),
    var registerHandlersResult: Boolean = true,
    var startDiscoveryResult: Boolean = true,
) : BlueZAdapterSession {
    var setDiscoveryFilterCalls: Int = 0
    var lastDiscoveryFilter: Map<String, Variant<*>>? = null
    var registerHandlersCalls: Int = 0
    var unregisterHandlersCalls: Int = 0
    var seedExistingDevicesCalls: Int = 0
    var pollDiscoveredDevicesCalls: Int = 0
    var startDiscoveryCalls: Int = 0
    var stopDiscoveryCalls: Int = 0
    var closeConnectionCalls: Int = 0
    val operationOrder: MutableList<String> = mutableListOf()

    private var propertiesChangedHandler: ((path: String, changed: Map<String, Any?>) -> Unit)? = null
    private var deviceAddedHandler: ((path: String, properties: Map<String, Any?>) -> Unit)? = null

    fun simulatePropertiesChanged(
        path: String,
        changed: Map<String, Any?>,
    ) {
        propertiesChangedHandler?.invoke(path, changed)
    }

    fun simulateDeviceAdded(
        path: String,
        properties: Map<String, Any?>,
    ) {
        deviceAddedHandler?.invoke(path, properties)
    }

    suspend fun awaitDiscoveryStarted() {
        withTimeout(2.seconds) {
            while (startDiscoveryCalls == 0) {
                delay(1)
            }
        }
    }

    suspend fun awaitDiscoveryStopped() {
        withTimeout(2.seconds) {
            while (stopDiscoveryCalls == 0) {
                delay(1)
            }
        }
    }

    suspend fun awaitSeedExistingDevices() {
        withTimeout(2.seconds) {
            while (seedExistingDevicesCalls == 0) {
                delay(1)
            }
        }
    }

    suspend fun awaitPollDiscoveredDevices() {
        withTimeout(2.seconds) {
            while (pollDiscoveredDevicesCalls == 0) {
                delay(1)
            }
        }
    }

    override fun setDiscoveryFilter(filters: Map<String, Variant<*>>): Result<Unit> {
        setDiscoveryFilterCalls++
        lastDiscoveryFilter = filters
        return setDiscoveryFilterResult
    }

    override fun registerHandlers(
        onPropertiesChanged: (path: String, changed: Map<String, Any?>) -> Unit,
        onDeviceAdded: (path: String, properties: Map<String, Any?>) -> Unit,
    ): Boolean {
        registerHandlersCalls++
        propertiesChangedHandler = onPropertiesChanged
        deviceAddedHandler = onDeviceAdded
        return registerHandlersResult
    }

    override fun unregisterHandlers() {
        unregisterHandlersCalls++
    }

    open override fun seedExistingDevices(onDevice: (BluetoothDevice) -> Unit) {
        seedExistingDevicesCalls++
        operationOrder.add("seedExistingDevices")
    }

    override fun pollDiscoveredDevices(onDevice: (BluetoothDevice) -> Unit) {
        pollDiscoveredDevicesCalls++
        operationOrder.add("pollDiscoveredDevices")
    }

    override fun startDiscovery(): Boolean {
        startDiscoveryCalls++
        operationOrder.add("startDiscovery")
        return startDiscoveryResult
    }

    override fun stopDiscovery() {
        stopDiscoveryCalls++
    }

    override fun closeConnection() {
        closeConnectionCalls++
    }
}

internal class FakeBlueZSessionFactory(
    private val session: FakeBlueZAdapterSession,
) : BlueZSessionFactory {
    var openCalls: Int = 0

    override fun open(): BlueZSessionOpenResult {
        openCalls++
        return BlueZSessionOpenResult.Ready(session)
    }
}
