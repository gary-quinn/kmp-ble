package com.atruedev.kmpble.scanner

import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import org.freedesktop.dbus.types.Variant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal class FakeBlueZAdapterSession(
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
    var startDiscoveryCalls: Int = 0
    var stopDiscoveryCalls: Int = 0
    var closeConnectionCalls: Int = 0

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
        return registerHandlersResult
    }

    override fun unregisterHandlers() {
        unregisterHandlersCalls++
    }

    override fun seedExistingDevices(onDevice: (BluetoothDevice) -> Unit) {
        seedExistingDevicesCalls++
    }

    override fun startDiscovery(): Boolean {
        startDiscoveryCalls++
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
