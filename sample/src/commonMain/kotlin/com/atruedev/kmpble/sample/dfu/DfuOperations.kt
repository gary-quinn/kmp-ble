package com.atruedev.kmpble.sample.dfu

import com.atruedev.kmpble.dfu.DfuController
import com.atruedev.kmpble.dfu.DfuProgress
import com.atruedev.kmpble.dfu.firmware.FirmwarePackage
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.sample.detail.DeviceDetailUiState
import com.atruedev.kmpble.sample.filepicker.FilePickerResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DfuOperations(
    private val peripheral: Peripheral,
    private val _uiState: MutableStateFlow<DeviceDetailUiState>,
    private val scope: CoroutineScope,
) {
    private var dfuJob: Job? = null
    private var currentFirmware: FirmwarePackage? = null

    fun selectFirmware(result: FilePickerResult) {
        try {
            val detection = FirmwareDetector.detect(result.name, result.bytes)
            currentFirmware = detection.firmware
            _uiState.update { it.copy(dfuState = DfuState.Ready(detection.info)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.update { it.copy(dfuState = DfuState.Failed("Invalid firmware: ${e.message}")) }
        }
    }

    fun startDfu() {
        val firmware = currentFirmware ?: return

        dfuJob?.cancel()
        dfuJob =
            scope.launch {
                try {
                    _uiState.update { it.copy(dfuState = DfuState.InProgress(0)) }
                    val controller = DfuController.create(peripheral)
                    controller.performDfu(firmware).collect { progress ->
                        when (progress) {
                            is DfuProgress.Transferring -> {
                                val percent = (progress.fraction * 100).toInt()
                                _uiState.update { it.copy(dfuState = DfuState.InProgress(percent)) }
                            }
                            is DfuProgress.Completed -> {
                                currentFirmware = null
                                _uiState.update { it.copy(dfuState = DfuState.Completed) }
                            }
                            is DfuProgress.Failed -> {
                                _uiState.update { it.copy(dfuState = DfuState.Failed(progress.error.toString())) }
                            }
                            else -> Unit
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(dfuState = DfuState.Failed("DFU failed: ${e.message}")) }
                }
            }
    }

    fun cancelDfu() {
        dfuJob?.cancel()
        dfuJob = null
        _uiState.update { it.copy(dfuState = DfuState.Idle) }
        currentFirmware = null
    }

    fun resetDfu() {
        _uiState.update { it.copy(dfuState = DfuState.Idle) }
        currentFirmware = null
    }
}
