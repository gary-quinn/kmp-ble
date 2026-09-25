package com.atruedev.kmpble.backend

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import kotlinx.coroutines.flow.StateFlow

/** Local adapter state for one backend. */
@KmpBleBackendApi
public interface AdapterTransport : AutoCloseable {
    public val state: StateFlow<BluetoothAdapterState>

    public val capabilities: AdapterCapabilities

    public fun bondedDevices(): List<Identifier>

    override fun close()
}

@KmpBleBackendApi
public class AdapterCapabilities(
    public val supportsExtendedAdvertising: Boolean = false,
    public val supportsLe2mPhy: Boolean = false,
    public val supportsLeCodedPhy: Boolean = false,
    public val supportsPeriodicAdvertising: Boolean = false,
)
