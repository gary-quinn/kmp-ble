package com.atruedev.kmpble.sample

import com.atruedev.kmpble.ExperimentalBleApi
import com.atruedev.kmpble.bonding.PairingEvent
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.bonding.PairingResponse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalBleApi::class)
class PairingCoordinator {

    private val _event = MutableStateFlow<PairingEvent?>(null)
    val event: StateFlow<PairingEvent?> = _event.asStateFlow()

    private val responseChannel = Channel<PairingResponse>(Channel.RENDEZVOUS)

    val handler = PairingHandler { event ->
        _event.value = event
        try {
            responseChannel.receive()
        } finally {
            _event.value = null
        }
    }

    fun respond(response: PairingResponse): Boolean =
        responseChannel.trySend(response).isSuccess
}
