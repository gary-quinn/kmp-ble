package com.atruedev.kmpble.peripheral

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.bonding.BondState
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.State

public interface PeripheralConnection {
    public val identifier: Identifier

    public suspend fun connect(options: ConnectionOptions = ConnectionOptions())

    public suspend fun disconnect()

    public val state: StateFlow<State>
    public val bondState: StateFlow<BondState>
}
