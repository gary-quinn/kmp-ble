package io.github.garyquinn.kmpble.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.garyquinn.kmpble.connection.ConnectionOptions
import io.github.garyquinn.kmpble.connection.State
import io.github.garyquinn.kmpble.gatt.BackpressureStrategy
import io.github.garyquinn.kmpble.gatt.Characteristic
import io.github.garyquinn.kmpble.gatt.Observation
import io.github.garyquinn.kmpble.gatt.WriteType
import io.github.garyquinn.kmpble.peripheral.Peripheral
import io.github.garyquinn.kmpble.scanner.Advertisement
import io.github.garyquinn.kmpble.peripheral.toPeripheral
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lifecycle-scoped peripheral management.
 *
 * This is the recommended pattern for using kmp-ble in a Compose app:
 * - The [Peripheral] is created once from the [Advertisement]
 * - All BLE operations are scoped to [viewModelScope]
 * - [onCleared] calls [Peripheral.close], which cancels coroutines and disconnects
 *
 * Without this pattern, GATT connections leak when the screen is removed from
 * the composition. On Android the OS-level connection persists, causing phantom
 * connections that drain battery and block reconnection.
 */
class BleViewModel(advertisement: Advertisement) : ViewModel() {

    private val peripheral: Peripheral = advertisement.toPeripheral()

    val connectionState: StateFlow<State> = peripheral.state
    val bondState = peripheral.bondState
    val services = peripheral.services
    val maximumWriteValueLength = peripheral.maximumWriteValueLength

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi: StateFlow<Int?> = _rssi.asStateFlow()

    private val _mtu = MutableStateFlow(23)
    val mtu: StateFlow<Int> = _mtu.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun connect(options: ConnectionOptions = ConnectionOptions()) {
        viewModelScope.launch {
            try {
                _error.value = null
                peripheral.connect(options)
            } catch (e: Exception) {
                _error.value = formatError(e)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                peripheral.disconnect()
            } catch (e: Exception) {
                _error.value = formatError(e)
            }
        }
    }

    fun readCharacteristic(characteristic: Characteristic, onResult: (Result<ByteArray>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { peripheral.read(characteristic) })
        }
    }

    fun writeCharacteristic(
        characteristic: Characteristic,
        data: ByteArray,
        writeType: WriteType = WriteType.WithResponse,
    ) {
        viewModelScope.launch {
            try {
                peripheral.write(characteristic, data, writeType)
            } catch (e: Exception) {
                _error.value = formatError(e)
            }
        }
    }

    fun observe(characteristic: Characteristic): Flow<Observation> =
        peripheral.observe(characteristic, BackpressureStrategy.Latest)

    fun readRssi() {
        viewModelScope.launch {
            try {
                _rssi.value = peripheral.readRssi()
            } catch (e: Exception) {
                _error.value = formatError(e)
            }
        }
    }

    fun requestMtu(mtu: Int) {
        viewModelScope.launch {
            try {
                _mtu.value = peripheral.requestMtu(mtu)
            } catch (e: Exception) {
                _error.value = formatError(e)
            }
        }
    }

    @OptIn(io.github.garyquinn.kmpble.ExperimentalBleApi::class)
    fun removeBond() {
        val result = peripheral.removeBond()
        _error.value = when (result) {
            is io.github.garyquinn.kmpble.bonding.BondRemovalResult.Success -> null
            is io.github.garyquinn.kmpble.bonding.BondRemovalResult.NotSupported -> result.message
            is io.github.garyquinn.kmpble.bonding.BondRemovalResult.Failed -> result.reason
        }
    }

    fun clearError() {
        _error.value = null
    }

    // Called when the composable hosting this ViewModel leaves the composition.
    // On Android: when the Activity/Fragment is destroyed (survives config changes).
    // On iOS: when the compose view is removed.
    //
    // This is critical — without it, the GATT connection leaks.
    override fun onCleared() {
        peripheral.close()
    }

    private fun formatError(e: Exception): String = e.message ?: "Unknown error"
}
