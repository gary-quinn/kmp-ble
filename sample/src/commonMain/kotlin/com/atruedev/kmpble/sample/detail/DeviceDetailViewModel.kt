package com.atruedev.kmpble.sample.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ReconnectionStrategy
import com.atruedev.kmpble.connection.State
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.sample.detail.bonding.BondingOperations
import com.atruedev.kmpble.sample.detail.bonding.ConnectionRecipeType
import com.atruedev.kmpble.sample.dfu.DfuOperations
import com.atruedev.kmpble.sample.filepicker.FilePickerResult
import com.atruedev.kmpble.sample.l2cap.L2capOperations
import com.atruedev.kmpble.sample.profiles.ProfileDetector
import com.atruedev.kmpble.sample.profiles.ProfileOperations
import com.atruedev.kmpble.sample.profiles.ProfileType
import com.atruedev.kmpble.sample.profiles.ProfileUiState
import com.atruedev.kmpble.sample.registry.BluetoothUuidNames
import com.atruedev.kmpble.scanner.Advertisement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class, com.atruedev.kmpble.ExperimentalBleApi::class)
class DeviceDetailViewModel(
    advertisement: Advertisement,
) : ViewModel() {
    private companion object {
        val GATT_OPERATION_TIMEOUT = 10.seconds
        const val MTU_MIN = 23
        const val MTU_MAX = 517
    }

    private val peripheral: Peripheral = advertisement.toPeripheral()

    private val _uiState =
        MutableStateFlow(
            DeviceDetailUiState(
                deviceName = advertisement.name ?: "Unknown",
                identifier = advertisement.identifier.value,
            ),
        )
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    private val charOps = CharacteristicOperations(peripheral, _uiState, viewModelScope)
    private val bondingOps = BondingOperations(peripheral, _uiState, viewModelScope)
    private val dfuOps = DfuOperations(peripheral, _uiState, viewModelScope)
    private val _profileState = MutableStateFlow(ProfileUiState())
    private val profileOps = ProfileOperations(peripheral, _profileState, viewModelScope)
    private val l2capOps = L2capOperations(peripheral, _uiState, viewModelScope)
    private var rssiJob: Job? = null

    init {
        observeConnectionState()
        observeBondState()
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            peripheral.state.collect { state ->
                _uiState.update { it.copy(connectionState = state, error = null) }
                when (state) {
                    is State.Connected.Ready -> onConnected()
                    is State.Disconnected -> onDisconnected()
                    else -> Unit
                }
            }
        }
    }

    private fun observeBondState() {
        viewModelScope.launch {
            peripheral.bondState.collect { bondState ->
                _uiState.update { it.copy(bondState = bondState) }
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                peripheral.connect(
                    ConnectionOptions(
                        timeout = 15.seconds,
                        reconnectionStrategy =
                            ReconnectionStrategy.ExponentialBackoff(
                                initialDelay = 1.seconds,
                                maxDelay = 15.seconds,
                                maxAttempts = 3,
                            ),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Connection failed: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            try {
                charOps.stopAllNotifications()
                peripheral.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Disconnect errors are non-critical
            }
        }
    }

    private fun onConnected() {
        discoverServices()
        startRssiPolling()
    }

    private fun onDisconnected() {
        rssiJob?.cancel()
        rssiJob = null
        charOps.stopAllNotifications()
        _uiState.update { it.copy(rssi = null, mtu = null) }
    }

    private fun discoverServices() {
        viewModelScope.launch {
            try {
                val services = withTimeout(GATT_OPERATION_TIMEOUT) { peripheral.refreshServices() }
                val serviceModels =
                    services.map { service ->
                        val characteristics =
                            service.characteristics.map { char ->
                                val descriptors =
                                    char.descriptors.map { desc ->
                                        DescriptorUiModel(
                                            descriptor = desc,
                                            uuid = desc.uuid,
                                            displayName = descriptorName(desc.uuid),
                                            lastReadValue = null,
                                        )
                                    }
                                CharacteristicUiModel(
                                    characteristic = char,
                                    uuid = char.uuid,
                                    displayName =
                                        BluetoothUuidNames.characteristicName(char.uuid)
                                            ?: char.uuid.toString().take(8),
                                    properties = char.properties,
                                    descriptors = descriptors,
                                )
                            }
                        ServiceUiModel(
                            uuid = service.uuid,
                            displayName =
                                BluetoothUuidNames.serviceName(service.uuid)
                                    ?: service.uuid.toString().take(8),
                            characteristics = characteristics,
                        )
                    }
                val detectedProfiles = ProfileDetector.detectProfiles(serviceModels)
                _profileState.update { it.copy(detectedProfiles = detectedProfiles) }
                _uiState.update { it.copy(services = serviceModels, profileState = _profileState.value) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Service discovery failed: ${e.message}") }
            }
        }
    }

    private fun descriptorName(uuid: kotlin.uuid.Uuid): String =
        BluetoothUuidNames.descriptorName(uuid) ?: uuid.toString().take(8)

    private fun startRssiPolling() {
        rssiJob?.cancel()
        rssiJob =
            viewModelScope.launch {
                var consecutiveFailures = 0
                while (true) {
                    try {
                        val rssi = withTimeout(GATT_OPERATION_TIMEOUT) { peripheral.readRssi() }
                        _uiState.update { it.copy(rssi = rssi) }
                        consecutiveFailures = 0
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        consecutiveFailures++
                    }
                    val backoff =
                        if (consecutiveFailures > 0) {
                            (2 * consecutiveFailures).coerceAtMost(10).seconds
                        } else {
                            2.seconds
                        }
                    delay(backoff)
                }
            }
    }

    fun requestMtu(mtu: Int) {
        require(mtu in MTU_MIN..MTU_MAX) { "MTU must be between $MTU_MIN and $MTU_MAX" }
        viewModelScope.launch {
            try {
                val negotiated = withTimeout(GATT_OPERATION_TIMEOUT) { peripheral.requestMtu(mtu) }
                _uiState.update { it.copy(mtu = negotiated) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "MTU request failed: ${e.message}") }
            }
        }
    }

    fun toggleService(serviceIndex: Int) = charOps.toggleService(serviceIndex)

    fun toggleCharacteristic(
        si: Int,
        ci: Int,
    ) = charOps.toggleCharacteristic(si, ci)

    fun readCharacteristic(
        si: Int,
        ci: Int,
    ) = charOps.readCharacteristic(si, ci)

    fun showWriteDialog(
        si: Int,
        ci: Int,
    ) = charOps.showWriteDialog(si, ci)

    fun dismissWriteDialog() = charOps.dismissWriteDialog()

    fun writeCharacteristic(
        si: Int,
        ci: Int,
        data: ByteArray,
        writeType: WriteType,
    ) = charOps.writeCharacteristic(si, ci, data, writeType)

    fun toggleNotifications(
        si: Int,
        ci: Int,
    ) = charOps.toggleNotifications(si, ci)

    fun setDisplayFormat(
        si: Int,
        ci: Int,
        format: DisplayFormat,
    ) = charOps.setDisplayFormat(si, ci, format)

    fun dismissCharacteristicError(
        si: Int,
        ci: Int,
    ) = charOps.dismissCharacteristicError(si, ci)

    fun pair() = bondingOps.pair()

    fun removeBond() = bondingOps.removeBond()

    fun selectRecipe(recipe: ConnectionRecipeType) = bondingOps.selectRecipe(recipe)

    fun connectWithRecipe(recipe: ConnectionRecipeType) = bondingOps.connectWithRecipe(recipe)

    fun selectFirmware(result: FilePickerResult) = dfuOps.selectFirmware(result)

    fun startDfu() = dfuOps.startDfu()

    fun cancelDfu() = dfuOps.cancelDfu()

    fun resetDfu() = dfuOps.resetDfu()

    fun startHeartRate() = profileOps.startHeartRate()

    fun stopProfile(type: ProfileType) = profileOps.stopProfile(type)

    fun readBattery() = profileOps.readBattery()

    fun startBatteryNotifications() = profileOps.startBatteryNotifications()

    fun readDeviceInfo() = profileOps.readDeviceInfo()

    fun openL2capChannel(psm: Int) = l2capOps.openChannel(psm)

    fun sendL2capData(data: ByteArray) = l2capOps.send(data)

    fun closeL2capChannel() = l2capOps.closeChannel()

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private var closed = false

    fun close() {
        if (closed) return
        closed = true
        charOps.stopAllNotifications()
        profileOps.stopAll()
        l2capOps.closeChannel()
        rssiJob?.cancel()
        peripheral.close()
    }

    override fun onCleared() {
        super.onCleared()
        close()
    }
}
