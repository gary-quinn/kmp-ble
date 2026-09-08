package com.atruedev.kmpble.scanner

import com.atruedev.kmpble.logging.BleLogEvent
import com.atruedev.kmpble.logging.logEvent
import com.atruedev.kmpble.scanner.internal.toScanEvents
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ConcurrentHashMap

/**
 * Linux BlueZ-backed [Scanner] using D-Bus ([org.bluez.Adapter1.StartDiscovery] and
 * [org.bluez.Device1] property updates).
 *
 * Use [BlueZScanner] to construct explicitly. The portable [Scanner] factory on JVM remains
 * disabled unless [BlueZ.ENABLE_PROPERTY] is set to `"true"`.
 */
public class BlueZScanner internal constructor(
    configure: ScannerConfig.() -> Unit = {},
    private val sessionFactory: BlueZSessionFactory = DefaultBlueZSessionFactory,
    coroutineScope: CoroutineScope? = null,
    private val seedSettleMs: Long = DEFAULT_SEED_SETTLE_MS,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : Scanner {
    public constructor(configure: ScannerConfig.() -> Unit = {}) : this(configure, DefaultBlueZSessionFactory)

    private val config = ScannerConfig().apply(configure)
    private val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val scanEvents: Flow<ScanEvent> = createRawScanFlow().toScanEvents(config, scope)

    override fun close() {
        scope.cancel()
    }

    private fun createRawScanFlow(): Flow<Advertisement> =
        callbackFlow {
            when (val opened = sessionFactory.open()) {
                is BlueZSessionOpenResult.Failed -> {
                    close(ScanFailedException(opened.code, opened.message))
                    return@callbackFlow
                }

                is BlueZSessionOpenResult.Ready -> {
                    runDiscoverySession(opened.session)
                }
            }
        }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<Advertisement>.runDiscoverySession(
        session: BlueZAdapterSession,
    ) {
        applyLeDiscoveryFilter(session)

        val snapshots = ConcurrentHashMap<String, BlueZDeviceSnapshot>()

        fun emitSnapshot(snapshot: BlueZDeviceSnapshot) {
            if (snapshot.rssi == null) return
            try {
                val merged =
                    snapshots.merge(
                        snapshot.dbusPath,
                        snapshot,
                    ) { previous, update ->
                        previous.merge(update)
                    }!!
                trySend(merged.toAdvertisement())
            } catch (error: Exception) {
                logEvent(
                    BleLogEvent.Warning(
                        identifier = null,
                        message =
                            "BlueZ advertisement mapping failed for ${snapshot.address} " +
                                "(${snapshot.dbusPath}): ${error.message}",
                    ),
                )
            }
        }

        fun emitDevice(device: BluetoothDevice) {
            try {
                device.toSnapshot()?.let(::emitSnapshot)
            } catch (error: Exception) {
                logEvent(
                    BleLogEvent.Warning(
                        identifier = null,
                        message = "BlueZ device snapshot failed for ${device.address}: ${error.message}",
                    ),
                )
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
                logEvent(
                    BleLogEvent.Warning(
                        identifier = null,
                        message = "BlueZ PropertiesChanged handling failed for $path: ${error.message}",
                    ),
                )
            }
        }

        fun onDeviceAdded(
            path: String,
            properties: Map<String, Any?>,
        ) {
            try {
                snapshotFromPropertyMap(path, properties)?.let(::emitSnapshot)
            } catch (error: Exception) {
                logEvent(
                    BleLogEvent.Warning(
                        identifier = null,
                        message = "BlueZ InterfacesAdded handling failed for $path: ${error.message}",
                    ),
                )
            }
        }

        if (!session.registerHandlers(::onPropertiesChanged, ::onDeviceAdded)) {
            session.closeConnection()
            close(
                ScanFailedException(
                    ERROR_DBUS_UNAVAILABLE,
                    "Failed to register BlueZ signal handlers",
                ),
            )
            return
        }

        if (!session.startDiscovery()) {
            session.unregisterHandlers()
            session.closeConnection()
            close(
                ScanFailedException(
                    ERROR_DISCOVERY_FAILED,
                    "BlueZ StartDiscovery failed",
                ),
            )
            return
        }

        logEvent(BleLogEvent.ScanStarted(config.filterGroups.size))

        val pollJob =
            launch(Dispatchers.Default) {
                delay(seedSettleMs)
                runCatching { session.seedExistingDevices(::emitDevice) }.onFailure { error ->
                    logPollFailure("seedExistingDevices", error)
                }
                while (isActive) {
                    delay(pollIntervalMs)
                    runCatching { session.pollDiscoveredDevices(::emitDevice) }.onFailure { error ->
                        logPollFailure("pollDiscoveredDevices", error)
                    }
                }
            }

        awaitClose {
            pollJob.cancel()
            session.stopDiscovery()
            session.unregisterHandlers()
            session.closeConnection()
            logEvent(BleLogEvent.ScanStopped("closed"))
        }
    }

    private fun logPollFailure(
        phase: String,
        error: Throwable,
    ) {
        logEvent(
            BleLogEvent.Warning(
                identifier = null,
                message = "BlueZ $phase failed: ${error.message}",
            ),
        )
    }

    private fun applyLeDiscoveryFilter(session: BlueZAdapterSession) {
        val filters =
            linkedMapOf<String, Variant<*>>(
                "Transport" to Variant("le"),
            )
        session.setDiscoveryFilter(filters).onFailure { error ->
            logEvent(
                BleLogEvent.Warning(
                    identifier = null,
                    message =
                        "BlueZ SetDiscoveryFilter failed (${error.message}); " +
                            "continuing with adapter defaults",
                ),
            )
        }
    }

    public companion object {
        public const val ERROR_DBUS_UNAVAILABLE: Int = -10
        public const val ERROR_NO_ADAPTER: Int = -11
        public const val ERROR_ADAPTER_OFF: Int = -12
        public const val ERROR_DISCOVERY_FAILED: Int = -13

        internal const val DEFAULT_SEED_SETTLE_MS: Long = 500L
        internal const val DEFAULT_POLL_INTERVAL_MS: Long = 750L
    }
}
