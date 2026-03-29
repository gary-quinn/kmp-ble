package com.atruedev.kmpble.sample.peripheral.advertising

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower
import com.atruedev.kmpble.server.Advertiser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class AdvertisingViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AdvertisingUiState())
    val uiState: StateFlow<AdvertisingUiState> = _uiState.asStateFlow()

    private val advertiser = Advertiser()

    fun setLocalName(name: String) {
        _uiState.update { it.copy(localName = name) }
    }

    fun toggleConnectable() {
        _uiState.update { it.copy(connectable = !it.connectable) }
    }

    fun setMode(mode: AdvertiseMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setTxPower(txPower: AdvertiseTxPower) {
        _uiState.update { it.copy(txPower = txPower) }
    }

    fun startAdvertising() {
        val state = _uiState.value
        viewModelScope.launch {
            try {
                advertiser.startAdvertising(
                    AdvertiseConfig(
                        name = state.localName.ifBlank { null },
                        connectable = state.connectable,
                        mode = state.mode,
                        txPower = state.txPower,
                    ),
                )
                _uiState.update { it.copy(state = AdvertisingState.Advertising) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = "${e::class.simpleName}: ${e.message}"
                _uiState.update { it.copy(state = AdvertisingState.Error(detail)) }
            }
        }
    }

    fun stopAdvertising() {
        viewModelScope.launch {
            try {
                advertiser.stopAdvertising()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Stop errors are non-critical
            }
            _uiState.update { it.copy(state = AdvertisingState.Stopped) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        advertiser.close()
    }
}
