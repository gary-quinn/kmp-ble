package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.macos.Macos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

/**
 * Process-wide owner of the CoreBluetooth central and peripheral managers behind [api].
 * Routes every native callback to the transport that owns its handle.
 */
internal class MacosStack(
    internal val api: MacosNativeApi,
) : NativeCallback {
    private val _centralState = MutableStateFlow(CbManagerState.UNKNOWN)
    val centralState: StateFlow<Int> = _centralState.asStateFlow()

    private val _peripheralManagerState = MutableStateFlow(CbManagerState.UNKNOWN)
    val peripheralManagerState: StateFlow<Int> = _peripheralManagerState.asStateFlow()

    private val initLock = Any()
    private var initialized = false
    private var centralStarted = false
    private var peripheralManagerStarted = false

    private val scanListeners = CopyOnWriteArrayList<(MacosEvent.Discovered) -> Unit>()
    private var scanUsers = 0
    private val scanLock = Any()

    private val peripheralListeners = ConcurrentHashMap<Long, (MacosEvent) -> Unit>()
    private val peripheralManagerListeners = CopyOnWriteArrayList<(MacosEvent) -> Unit>()
    private val channelLock = Any()
    private val channelListeners = HashMap<Long, (MacosEvent) -> Unit>()
    private val orphanChannelEvents = HashMap<Long, MutableList<MacosEvent>>()

    /** CoreBluetooth advertises one set per process; holds the advertiser that owns it. */
    val advertisingOwner = AtomicReference<Any?>(null)

    /** `didPublishL2CAPChannel` carries no correlation id, so publishes run one at a time. */
    val l2capPublishLock = Mutex()

    fun ensureCentral() {
        synchronized(initLock) {
            initialize()
            if (centralStarted) return
            missingUsageDescriptionHost()?.let { throw MissingUsageDescriptionException(it) }
            api.centralStart()
            centralStarted = true
        }
    }

    fun ensurePeripheralManager() {
        synchronized(initLock) {
            initialize()
            if (peripheralManagerStarted) return
            missingUsageDescriptionHost()?.let { throw MissingUsageDescriptionException(it) }
            api.pmStart()
            peripheralManagerStarted = true
        }
    }

    /**
     * The host app that would make macOS terminate this process on first CoreBluetooth use, or
     * null. Skipped when [Macos.USAGE_DESCRIPTION_CHECK_PROPERTY] is `false`.
     */
    fun missingUsageDescriptionHost(): String? {
        if (System.getProperty(Macos.USAGE_DESCRIPTION_CHECK_PROPERTY) == "false") return null
        synchronized(initLock) { initialize() }
        return api.missingUsageDescriptionHost()
    }

    /** Waits until the central leaves `Unknown`/`Resetting`; returns the settled state. */
    suspend fun awaitCentralSettled(timeout: Duration): Int {
        ensureCentral()
        _centralState.compareAndSet(CbManagerState.UNKNOWN, api.centralState())
        return withTimeoutOrNull(timeout) { centralState.first { it.isSettled() } } ?: _centralState.value
    }

    suspend fun awaitPeripheralManagerSettled(timeout: Duration): Int {
        ensurePeripheralManager()
        _peripheralManagerState.compareAndSet(CbManagerState.UNKNOWN, api.pmState())
        return withTimeoutOrNull(timeout) { peripheralManagerState.first { it.isSettled() } }
            ?: _peripheralManagerState.value
    }

    fun addScanListener(listener: (MacosEvent.Discovered) -> Unit): AutoCloseable {
        scanListeners += listener
        synchronized(scanLock) {
            if (scanUsers++ == 0) api.scanStart(null)
        }
        return AutoCloseable {
            if (!scanListeners.remove(listener)) return@AutoCloseable
            synchronized(scanLock) {
                if (--scanUsers == 0) api.scanStop()
            }
        }
    }

    fun registerPeripheral(
        handle: Long,
        listener: (MacosEvent) -> Unit,
    ) {
        peripheralListeners[handle] = listener
    }

    fun unregisterPeripheral(handle: Long) {
        peripheralListeners.remove(handle)
    }

    fun addPeripheralManagerListener(listener: (MacosEvent) -> Unit): AutoCloseable {
        peripheralManagerListeners += listener
        return AutoCloseable { peripheralManagerListeners.remove(listener) }
    }

    fun registerChannel(
        channel: Long,
        listener: (MacosEvent) -> Unit,
    ) {
        synchronized(channelLock) {
            channelListeners[channel] = listener
            orphanChannelEvents.remove(channel)?.forEach(listener)
        }
    }

    fun unregisterChannel(channel: Long) {
        synchronized(channelLock) {
            channelListeners.remove(channel)
            orphanChannelEvents.remove(channel)
        }
    }

    override fun onEvent(
        kind: Int,
        a: Long,
        b: Long,
        status: Int,
        text: String?,
        data: ByteArray?,
    ) {
        val event = MacosEvent.decode(kind, a, b, status, text, data) ?: return
        dispatch(event)
    }

    internal fun dispatch(event: MacosEvent) {
        when (event) {
            is MacosEvent.CentralState -> {
                _centralState.value = event.state
                if (event.state != CbManagerState.POWERED_ON) peripheralListeners.values.forEach { it(event) }
            }
            is MacosEvent.Discovered -> scanListeners.forEach { it(event) }
            is MacosEvent.L2capData -> deliverToChannel(event.channel, event)
            is MacosEvent.L2capClosed -> deliverToChannel(event.channel, event)
            is MacosEvent.PmState -> {
                _peripheralManagerState.value = event.state
                peripheralManagerListeners.forEach { it(event) }
            }
            else -> {
                val peripheral = event.peripheralHandle()
                if (peripheral != null) {
                    peripheralListeners[peripheral]?.invoke(event)
                } else {
                    peripheralManagerListeners.forEach { it(event) }
                }
            }
        }
    }

    private fun deliverToChannel(
        channel: Long,
        event: MacosEvent,
    ) {
        synchronized(channelLock) {
            val listener = channelListeners[channel]
            if (listener != null) {
                listener(event)
            } else {
                orphanChannelEvents.getOrPut(channel) { mutableListOf() }.add(event)
            }
        }
    }

    private fun initialize() {
        if (initialized) return
        api.init(this)
        initialized = true
    }

    private fun Int.isSettled(): Boolean = this != CbManagerState.UNKNOWN && this != CbManagerState.RESETTING

    private fun MacosEvent.peripheralHandle(): Long? =
        when (this) {
            is MacosEvent.Connected -> peripheral
            is MacosEvent.ConnectFailed -> peripheral
            is MacosEvent.Disconnected -> peripheral
            is MacosEvent.ServicesDiscovered -> peripheral
            is MacosEvent.CharacteristicsDiscovered -> peripheral
            is MacosEvent.DescriptorsDiscovered -> peripheral
            is MacosEvent.ValueUpdated -> peripheral
            is MacosEvent.ValueWritten -> peripheral
            is MacosEvent.DescriptorValue -> peripheral
            is MacosEvent.DescriptorWritten -> peripheral
            is MacosEvent.NotifyState -> peripheral
            is MacosEvent.Rssi -> peripheral
            is MacosEvent.ReadyToWrite -> peripheral
            is MacosEvent.ServicesModified -> peripheral
            is MacosEvent.L2capOpened -> peripheral
            else -> null
        }

    internal companion object {
        val shared: MacosStack by lazy { MacosStack(JniMacosNativeApi) }
    }
}

/** [host] lacks `NSBluetoothAlwaysUsageDescription`; macOS would terminate the process. */
internal class MissingUsageDescriptionException(
    host: String,
) : IllegalStateException(
        "macOS attributes this process's Bluetooth use to $host, whose Info.plist has no " +
            "NSBluetoothAlwaysUsageDescription, and would terminate the process on first CoreBluetooth " +
            "use. Launch from a host that declares the key (Terminal.app, or a packaged .app built with " +
            "jpackage --resource-dir), or set -D${Macos.USAGE_DESCRIPTION_CHECK_PROPERTY}=false to skip " +
            "this check. Error code ${Macos.ERROR_USAGE_DESCRIPTION_MISSING}.",
    )
