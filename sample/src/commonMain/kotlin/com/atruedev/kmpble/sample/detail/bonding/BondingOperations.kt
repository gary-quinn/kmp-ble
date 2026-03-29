package com.atruedev.kmpble.sample.detail.bonding

import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.ConnectionRecipe
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.sample.detail.DeviceDetailUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(com.atruedev.kmpble.ExperimentalBleApi::class)
internal class BondingOperations(
    private val peripheral: Peripheral,
    private val _uiState: MutableStateFlow<DeviceDetailUiState>,
    private val scope: CoroutineScope,
) {
    fun pair() {
        scope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                val recipe = _uiState.value.selectedRecipe ?: ConnectionRecipeType.CONSUMER
                peripheral.connect(connectionOptionsForRecipe(recipe))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Pairing failed: ${e.message}") }
            }
        }
    }

    fun removeBond() {
        scope.launch {
            try {
                _uiState.update { it.copy(error = null) }
                peripheral.removeBond()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Remove bond failed: ${e.message}") }
            }
        }
    }

    fun connectWithRecipe(recipe: ConnectionRecipeType) {
        scope.launch {
            try {
                _uiState.update { it.copy(error = null, selectedRecipe = recipe) }
                peripheral.connect(connectionOptionsForRecipe(recipe))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Connection failed: ${e.message}") }
            }
        }
    }

    fun selectRecipe(recipe: ConnectionRecipeType) {
        _uiState.update { it.copy(selectedRecipe = recipe) }
    }
}

internal fun connectionOptionsForRecipe(recipe: ConnectionRecipeType): ConnectionOptions =
    when (recipe) {
        ConnectionRecipeType.MEDICAL -> ConnectionRecipe.MEDICAL
        ConnectionRecipeType.FITNESS -> ConnectionRecipe.FITNESS
        ConnectionRecipeType.IOT -> ConnectionRecipe.IOT
        ConnectionRecipeType.CONSUMER -> ConnectionRecipe.CONSUMER
    }
