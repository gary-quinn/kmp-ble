package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.backend.ScanRecord
import com.atruedev.kmpble.backend.ScanRequest
import com.atruedev.kmpble.backend.ScanTransport
import com.atruedev.kmpble.backend.backendLog
import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.scanner.ScanFailedException
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ConcurrentHashMap

/**
 * BlueZ discovery through `Adapter1.StartDiscovery`, `InterfacesAdded`, and `Device1`
 * `PropertiesChanged`, with a periodic sweep for devices whose properties BlueZ updates
 * without a signal.
 */
internal class BlueZScanTransport(
    private val sessionFactory: BlueZSessionFactory = DefaultBlueZSessionFactory,
    private val seedSettleMs: Long = DEFAULT_SEED_SETTLE_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : ScanTransport {
    override fun scan(request: ScanRequest): Flow<ScanRecord> =
        callbackFlow {
            when (val opened = sessionFactory.open()) {
                is BlueZSessionOpenResult.Failed -> close(ScanFailedException(opened.code, opened.message))
                is BlueZSessionOpenResult.Ready -> runDiscovery(opened.session, request)
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun ProducerScope<ScanRecord>.runDiscovery(
        session: BlueZAdapterSession,
        request: ScanRequest,
    ) {
        applyDiscoveryFilter(session, request)

        val snapshots = ConcurrentHashMap<String, BlueZDeviceSnapshot>()

        fun emitSnapshot(snapshot: BlueZDeviceSnapshot) {
            if (snapshot.rssi == null) return
            try {
                val merged =
                    snapshots.merge(
                        snapshot.dbusPath,
                        snapshot,
                    ) { previous, update -> previous.merge(update) }!!
                trySend(merged.toScanRecord())
            } catch (error: Exception) {
                warn("advertisement mapping failed for ${snapshot.address} (${snapshot.dbusPath}): ${error.message}")
            }
        }

        fun emitDevice(device: BluetoothDevice) {
            try {
                device.toSnapshot()?.let(::emitSnapshot)
            } catch (error: Exception) {
                warn("device snapshot failed for ${device.address}: ${error.message}")
            }
        }

        fun onPropertiesChanged(
            path: String,
            changed: Map<String, Any?>,
        ) {
            try {
                val existing = snapshots[path]
                val address = existing?.address ?: (changed["Address"] as? String) ?: return
                val delta = snapshotFromChangedProperties(path, address, changed)
                emitSnapshot((existing ?: delta).merge(delta))
            } catch (error: Exception) {
                warn("PropertiesChanged handling failed for $path: ${error.message}")
            }
        }

        fun onDeviceAdded(
            path: String,
            properties: Map<String, Any?>,
        ) {
            try {
                snapshotFromPropertyMap(path, properties)?.let(::emitSnapshot)
            } catch (error: Exception) {
                warn("InterfacesAdded handling failed for $path: ${error.message}")
            }
        }

        if (!session.registerHandlers(::onPropertiesChanged, ::onDeviceAdded)) {
            session.closeConnection()
            close(ScanFailedException(BlueZScanner.ERROR_DBUS_UNAVAILABLE, "Failed to register BlueZ signal handlers"))
            return
        }

        if (!session.startDiscovery()) {
            session.unregisterHandlers()
            session.closeConnection()
            close(ScanFailedException(BlueZScanner.ERROR_DISCOVERY_FAILED, "BlueZ StartDiscovery failed"))
            return
        }

        val pollJob =
            launch(Dispatchers.IO) {
                delay(seedSettleMs)
                runCatching { session.seedExistingDevices(::emitDevice) }
                    .onFailure { warn("seedExistingDevices failed: ${it.message}") }
                while (isActive) {
                    delay(pollIntervalMs)
                    runCatching { session.pollDiscoveredDevices(::emitDevice) }
                        .onFailure { warn("pollDiscoveredDevices failed: ${it.message}") }
                }
            }

        awaitClose {
            pollJob.cancel()
            runCatching { session.stopDiscovery() }
            session.unregisterHandlers()
            session.closeConnection()
        }
    }

    private fun applyDiscoveryFilter(
        session: BlueZAdapterSession,
        request: ScanRequest,
    ) {
        val filter = linkedMapOf<String, Variant<*>>("Transport" to Variant("le"), "DuplicateData" to Variant(true))
        if (request.serviceUuids.isNotEmpty()) {
            filter["UUIDs"] = Variant(request.serviceUuids.map { it.toString() }.toTypedArray())
        }
        session.setDiscoveryFilter(filter).onFailure { error ->
            warn("SetDiscoveryFilter failed (${error.message}); continuing with adapter defaults")
        }
    }

    private fun warn(message: String) {
        backendLog(BleLogEvent.Warning(identifier = null, message = "BlueZ $message"))
    }

    internal companion object {
        const val DEFAULT_SEED_SETTLE_MS: Long = 500L
        const val DEFAULT_POLL_INTERVAL_MS: Long = 750L
    }
}
