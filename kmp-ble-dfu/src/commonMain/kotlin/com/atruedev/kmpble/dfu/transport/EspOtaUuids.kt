package com.atruedev.kmpble.dfu.transport

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal object EspOtaUuids {
    val OTA_SERVICE: Uuid = Uuid.parse("d6f1d96d-594c-4c53-b1c6-244a1dfde6d8")
    val OTA_CONTROL: Uuid = Uuid.parse("7ad671aa-21c0-46a4-b722-270e3ae3d830")
    val OTA_DATA: Uuid = Uuid.parse("23408888-1f40-4cd8-9b89-ca8d45f8a5b0")
}
