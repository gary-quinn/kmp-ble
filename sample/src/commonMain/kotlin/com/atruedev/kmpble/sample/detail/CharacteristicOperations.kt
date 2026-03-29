package com.atruedev.kmpble.sample.detail

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.WriteType
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal class CharacteristicOperations(
    private val peripheral: Peripheral,
    private val _uiState: MutableStateFlow<DeviceDetailUiState>,
    private val scope: CoroutineScope,
) {
    private val notificationJobs = mutableMapOf<String, Job>()

    private companion object {
        const val MAX_NOTIFICATION_VALUES = 50
        val GATT_OPERATION_TIMEOUT = 10.seconds
    }

    fun toggleService(serviceIndex: Int) {
        _uiState.update { state ->
            val services = state.services.toMutableList()
            val service = services[serviceIndex]
            services[serviceIndex] = service.copy(isExpanded = !service.isExpanded)
            state.copy(services = services)
        }
    }

    fun toggleCharacteristic(
        serviceIndex: Int,
        charIndex: Int,
    ) {
        _uiState.update { state ->
            val services = state.services.toMutableList()
            val service = services[serviceIndex]
            val chars = service.characteristics.toMutableList()
            val char = chars[charIndex]
            chars[charIndex] = char.copy(isExpanded = !char.isExpanded)
            services[serviceIndex] = service.copy(characteristics = chars)
            state.copy(services = services)
        }
    }

    fun readCharacteristic(
        serviceIndex: Int,
        charIndex: Int,
    ) {
        val char = _uiState.value.services[serviceIndex].characteristics[charIndex]
        scope.launch {
            try {
                val value = withTimeout(GATT_OPERATION_TIMEOUT) { peripheral.read(char.characteristic) }
                updateCharacteristic(serviceIndex, charIndex) {
                    it.copy(lastReadValue = value, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateCharacteristic(serviceIndex, charIndex) {
                    it.copy(error = "Read failed: ${e.message}")
                }
            }
        }
    }

    fun showWriteDialog(
        serviceIndex: Int,
        charIndex: Int,
    ) {
        val char = _uiState.value.services[serviceIndex].characteristics[charIndex]
        _uiState.update { it.copy(writeDialogTarget = char) }
    }

    fun dismissWriteDialog() {
        _uiState.update { it.copy(writeDialogTarget = null) }
    }

    fun writeCharacteristic(
        serviceIndex: Int,
        charIndex: Int,
        data: ByteArray,
        writeType: WriteType,
    ) {
        val char = _uiState.value.services[serviceIndex].characteristics[charIndex]
        scope.launch {
            try {
                withTimeout(GATT_OPERATION_TIMEOUT) { peripheral.write(char.characteristic, data, writeType) }
                updateCharacteristic(serviceIndex, charIndex) {
                    it.copy(lastReadValue = data, error = null)
                }
                dismissWriteDialog()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateCharacteristic(serviceIndex, charIndex) {
                    it.copy(error = "Write failed: ${e.message}")
                }
            }
        }
    }

    fun toggleNotifications(
        serviceIndex: Int,
        charIndex: Int,
    ) {
        val char = _uiState.value.services[serviceIndex].characteristics[charIndex]
        val key = "${serviceIndex}_$charIndex"

        if (char.isNotifying) {
            notificationJobs[key]?.cancel()
            notificationJobs.remove(key)
            updateCharacteristic(serviceIndex, charIndex) {
                it.copy(isNotifying = false)
            }
        } else {
            updateCharacteristic(serviceIndex, charIndex) {
                it.copy(isNotifying = true, notificationValues = emptyList())
            }
            // Buffer(16) suspends the producer when full, applying backpressure
            // to the BLE stack. Notifications resume once the collector drains items.
            val job =
                scope.launch {
                    try {
                        peripheral
                            .observeValues(
                                char.characteristic,
                                BackpressureStrategy.Buffer(capacity = 16),
                            ).collect { value ->
                                appendNotificationValue(serviceIndex, charIndex, value)
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        updateCharacteristic(serviceIndex, charIndex) {
                            it.copy(isNotifying = false, error = "Notification failed: ${e.message}")
                        }
                    }
                }
            notificationJobs[key] = job
        }
    }

    fun setDisplayFormat(
        serviceIndex: Int,
        charIndex: Int,
        format: DisplayFormat,
    ) {
        updateCharacteristic(serviceIndex, charIndex) {
            it.copy(displayFormat = format)
        }
    }

    fun dismissCharacteristicError(
        serviceIndex: Int,
        charIndex: Int,
    ) {
        updateCharacteristic(serviceIndex, charIndex) {
            it.copy(error = null)
        }
    }

    // Accumulate values in a reusable ArrayDeque to avoid allocating a new
    // MutableList on every BLE notification. The deque is snapshotted to an
    // immutable list only once per update for the UI state.
    private val notificationBuffers = mutableMapOf<String, ArrayDeque<TimestampedValue>>()

    private fun appendNotificationValue(
        serviceIndex: Int,
        charIndex: Int,
        value: ByteArray,
    ) {
        val key = "${serviceIndex}_$charIndex"
        val buffer = notificationBuffers.getOrPut(key) { ArrayDeque(MAX_NOTIFICATION_VALUES) }
        buffer.addLast(TimestampedValue(value, Clock.System.now()))
        while (buffer.size > MAX_NOTIFICATION_VALUES) {
            buffer.removeFirst()
        }
        updateCharacteristic(serviceIndex, charIndex) { model ->
            model.copy(
                notificationValues = buffer.toList(),
                lastReadValue = value,
                error = null,
            )
        }
    }

    fun stopAllNotifications() {
        notificationJobs.values.forEach { it.cancel() }
        notificationJobs.clear()
        notificationBuffers.clear()
    }

    private fun updateCharacteristic(
        serviceIndex: Int,
        charIndex: Int,
        transform: (CharacteristicUiModel) -> CharacteristicUiModel,
    ) {
        _uiState.update { state ->
            val services = state.services.toMutableList()
            if (serviceIndex >= services.size) return@update state
            val service = services[serviceIndex]
            val chars = service.characteristics.toMutableList()
            if (charIndex >= chars.size) return@update state
            chars[charIndex] = transform(chars[charIndex])
            services[serviceIndex] = service.copy(characteristics = chars)
            state.copy(services = services)
        }
    }
}
