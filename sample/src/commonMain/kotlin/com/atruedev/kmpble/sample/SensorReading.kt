@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.atruedev.kmpble.sample

import com.atruedev.kmpble.codec.serialization.cborCodec
import kotlinx.serialization.Serializable

/**
 * Minimal typed payload streamed over a framed L2CAP channel in the demo.
 *
 * Lives in the sample module on purpose: this is a usage example, not a
 * library type. Real consumers define their own `@Serializable` types and
 * wire them through `cborCodec<T>()` the same way.
 */
@Serializable
data class SensorReading(
    val timestampMs: Long,
    val celsius: Double,
)

val SensorReadingCodec = cborCodec<SensorReading>()
