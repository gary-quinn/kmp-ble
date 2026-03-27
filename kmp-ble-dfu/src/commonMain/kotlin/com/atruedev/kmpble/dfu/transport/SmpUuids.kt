package com.atruedev.kmpble.dfu.transport

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal object SmpUuids {
    val SMP_SERVICE: Uuid = Uuid.parse("8D53DC1D-1DB7-4CD3-868B-8A527460AA84")
    val SMP_CHARACTERISTIC: Uuid = Uuid.parse("DA2E7828-FBCE-4E01-AE9E-261174997C48")
}
