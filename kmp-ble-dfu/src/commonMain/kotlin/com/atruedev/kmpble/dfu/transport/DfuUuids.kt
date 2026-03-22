package com.atruedev.kmpble.dfu.transport

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal object DfuUuids {
    val DFU_SERVICE: Uuid = Uuid.parse("0000fe59-0000-1000-8000-00805f9b34fb")
    val DFU_CONTROL_POINT: Uuid = Uuid.parse("8ec90001-f315-4f60-9fb8-838830daea50")
    val DFU_PACKET: Uuid = Uuid.parse("8ec90002-f315-4f60-9fb8-838830daea50")

    val BUTTONLESS_DFU: Uuid = Uuid.parse("8ec90003-f315-4f60-9fb8-838830daea50")
    val BUTTONLESS_DFU_WITHOUT_BOND: Uuid = Uuid.parse("8ec90004-f315-4f60-9fb8-838830daea50")
}
