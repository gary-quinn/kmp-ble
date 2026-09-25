package com.atruedev.kmpble.backend.internal

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BleCapabilities
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.backend.AdapterTransport
import com.atruedev.kmpble.backend.KmpBleBackendApi
import kotlinx.coroutines.flow.StateFlow

@OptIn(KmpBleBackendApi::class)
internal class BackendBluetoothAdapter(
    private val transport: AdapterTransport,
) : BluetoothAdapter {
    override val state: StateFlow<BluetoothAdapterState> get() = transport.state

    override val capabilities: BleCapabilities by lazy {
        val caps = transport.capabilities
        BleCapabilities(
            supportsExtendedAdvertising = caps.supportsExtendedAdvertising,
            supportsLe2mPhy = caps.supportsLe2mPhy,
            supportsLeCodedPhy = caps.supportsLeCodedPhy,
            supportsPeriodicAdvertising = caps.supportsPeriodicAdvertising,
            supportsLePowerControl = false,
            supportsLeAudio = false,
            supportsConnectionSubrating = false,
            supportsPast = false,
            supportsDirectionFinding = false,
        )
    }

    override fun getBondedDevices(): List<Identifier> = transport.bondedDevices()

    override fun close() {
        transport.close()
    }
}
