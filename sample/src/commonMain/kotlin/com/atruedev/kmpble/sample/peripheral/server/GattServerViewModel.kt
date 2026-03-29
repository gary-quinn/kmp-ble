package com.atruedev.kmpble.sample.peripheral.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.server.GattServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class GattServerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GattServerUiState())
    val uiState: StateFlow<GattServerUiState> = _uiState.asStateFlow()

    private var server: GattServer? = null

    fun loadPreset(preset: ServerPreset) {
        _uiState.update { it.copy(services = preset.services) }
    }

    fun addService() {
        _uiState.update { state ->
            val services =
                state.services +
                    ServerServiceConfig(
                        characteristics = listOf(ServerCharConfig()),
                    )
            state.copy(services = services)
        }
    }

    fun removeService(index: Int) {
        _uiState.update { state ->
            val services = state.services.toMutableList().apply { removeAt(index) }
            state.copy(services = services)
        }
    }

    fun updateServiceUuid(
        serviceIndex: Int,
        uuid: String,
    ) {
        _uiState.update { state ->
            val services = state.services.toMutableList()
            services[serviceIndex] = services[serviceIndex].copy(uuid = uuid)
            state.copy(services = services)
        }
    }

    fun addCharacteristic(serviceIndex: Int) {
        _uiState.update { state ->
            val services = state.services.toMutableList()
            val service = services[serviceIndex]
            services[serviceIndex] =
                service.copy(
                    characteristics = service.characteristics + ServerCharConfig(),
                )
            state.copy(services = services)
        }
    }

    fun updateCharUuid(
        serviceIndex: Int,
        charIndex: Int,
        uuid: String,
    ) {
        updateChar(serviceIndex, charIndex) { it.copy(uuid = uuid) }
    }

    fun toggleCharProperty(
        serviceIndex: Int,
        charIndex: Int,
        property: CharProperty,
    ) {
        updateChar(serviceIndex, charIndex) { char ->
            val props = if (property in char.properties) char.properties - property else char.properties + property
            char.copy(properties = props)
        }
    }

    fun startServer() {
        viewModelScope.launch {
            try {
                val configs = _uiState.value.services

                server?.close()
                val gattServer =
                    GattServer {
                        configs.forEach { serviceConfig ->
                            service(Uuid.parse(serviceConfig.uuid)) {
                                serviceConfig.characteristics.forEach { charConfig ->
                                    characteristic(Uuid.parse(charConfig.uuid)) {
                                        properties {
                                            read = CharProperty.READ in charConfig.properties
                                            write = CharProperty.WRITE in charConfig.properties
                                            notify = CharProperty.NOTIFY in charConfig.properties
                                        }
                                        permissions {
                                            read = CharProperty.READ in charConfig.properties
                                            write = CharProperty.WRITE in charConfig.properties
                                        }
                                        if (CharProperty.READ in charConfig.properties) {
                                            onRead { _ -> BleData(charConfig.readValue) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                gattServer.open()
                server = gattServer
                _uiState.update { it.copy(state = ServerState.Running, error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = "${e::class.simpleName}: ${e.message}"
                _uiState.update { it.copy(state = ServerState.Error(detail), error = detail) }
            }
        }
    }

    fun stopServer() {
        server?.close()
        server = null
        _uiState.update { it.copy(state = ServerState.Stopped, error = null) }
    }

    private fun updateChar(
        serviceIndex: Int,
        charIndex: Int,
        transform: (ServerCharConfig) -> ServerCharConfig,
    ) {
        _uiState.update { state ->
            val services = state.services.toMutableList()
            val service = services[serviceIndex]
            val chars = service.characteristics.toMutableList()
            chars[charIndex] = transform(chars[charIndex])
            services[serviceIndex] = service.copy(characteristics = chars)
            state.copy(services = services)
        }
    }

    override fun onCleared() {
        super.onCleared()
        server?.close()
    }
}
