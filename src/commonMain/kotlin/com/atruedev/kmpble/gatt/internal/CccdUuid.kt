package com.atruedev.kmpble.gatt.internal

import com.atruedev.kmpble.scanner.uuidFrom
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal val CCCD_UUID: Uuid = uuidFrom("2902")

internal val ENABLE_NOTIFICATION_VALUE = byteArrayOf(0x01, 0x00)
internal val ENABLE_INDICATION_VALUE = byteArrayOf(0x02, 0x00)
internal val DISABLE_NOTIFICATION_VALUE = byteArrayOf(0x00, 0x00)
