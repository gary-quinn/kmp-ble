package com.atruedev.kmpble.codec

import com.atruedev.kmpble.scanner.Advertisement
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Decode manufacturer-specific data. Zero-copy on iOS. */
public fun <T> Advertisement.decodeManufacturerData(
    companyId: Int,
    decoder: BleDataDecoder<T>,
): T? = manufacturerData[companyId]?.let(decoder::decode)

/** Decode service-specific data. Zero-copy on iOS. */
@OptIn(ExperimentalUuidApi::class)
public fun <T> Advertisement.decodeServiceData(
    serviceUuid: Uuid,
    decoder: BleDataDecoder<T>,
): T? = serviceData[serviceUuid]?.let(decoder::decode)
