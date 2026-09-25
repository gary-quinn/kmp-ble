package com.atruedev.kmpble.macos.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.backend.AdapterCapabilities
import com.atruedev.kmpble.backend.AdapterTransport
import com.atruedev.kmpble.permissions.PermissionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `CBManager.state` of the shared central. CoreBluetooth starts on the first read of [state],
 * which is when macOS may show the Bluetooth permission prompt.
 */
internal class MacosAdapterTransport(
    private val stack: MacosStack,
) : AdapterTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)
    private val _state = MutableStateFlow<BluetoothAdapterState>(BluetoothAdapterState.Unavailable)

    override val state: StateFlow<BluetoothAdapterState>
        get() {
            if (started.compareAndSet(false, true)) start()
            return _state.asStateFlow()
        }

    override val capabilities: AdapterCapabilities =
        AdapterCapabilities(
            supportsExtendedAdvertising = false,
            supportsLe2mPhy = true,
            supportsLeCodedPhy = true,
            supportsPeriodicAdvertising = false,
        )

    override fun bondedDevices(): List<Identifier> = emptyList()

    override fun close() {
        scope.cancel()
    }

    private fun start() {
        try {
            stack.ensureCentral()
        } catch (_: MissingUsageDescriptionException) {
            _state.value = BluetoothAdapterState.Unauthorized
            return
        } catch (_: UnsatisfiedLinkError) {
            _state.value = BluetoothAdapterState.Unsupported
            return
        }
        scope.launch { stack.centralState.collect { _state.value = it.toAdapterState() } }
    }
}

internal fun Int.toAdapterState(): BluetoothAdapterState =
    when (this) {
        CbManagerState.POWERED_ON -> BluetoothAdapterState.On
        CbManagerState.POWERED_OFF -> BluetoothAdapterState.Off
        CbManagerState.UNAUTHORIZED -> BluetoothAdapterState.Unauthorized
        CbManagerState.UNSUPPORTED -> BluetoothAdapterState.Unsupported
        else -> BluetoothAdapterState.Unavailable
    }

/** `CBManager.authorization` plus the Info.plist check that decides whether CoreBluetooth may start. */
internal object MacosPermissions {
    const val BLUETOOTH = "bluetooth"
    const val USAGE_DESCRIPTION = "NSBluetoothAlwaysUsageDescription"

    private const val NOT_DETERMINED = 0
    private const val RESTRICTED = 1
    private const val DENIED = 2
    private const val ALLOWED_ALWAYS = 3

    fun check(stack: MacosStack): PermissionResult =
        try {
            if (stack.missingUsageDescriptionHost() != null) {
                PermissionResult.PermanentlyDenied(listOf(USAGE_DESCRIPTION))
            } else {
                when (stack.api.authorization()) {
                    ALLOWED_ALWAYS -> PermissionResult.Granted
                    RESTRICTED, DENIED -> PermissionResult.PermanentlyDenied(listOf(BLUETOOTH))
                    NOT_DETERMINED -> PermissionResult.Denied(listOf(BLUETOOTH))
                    else -> PermissionResult.Denied(listOf(BLUETOOTH))
                }
            }
        } catch (_: UnsatisfiedLinkError) {
            PermissionResult.Denied(listOf("kmp-ble-macos native library"))
        }
}
