package com.atruedev.kmpble.observation

import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.internal.ObservationKey
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import com.atruedev.kmpble.gatt.internal.deserializeBackpressure
import com.atruedev.kmpble.gatt.internal.serializeBackpressure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Cross-platform roundtrip tests for PersistedObservation serialization.
 *
 * Both Android (SharedPreferences + JsonArrayEncoder) and iOS (NSUserDefaults)
 * implementations use the same map-key format {"s": serviceUuid, "c": charUuid,
 * "bp": serializedBackpressure} with [serializeBackpressure] and
 * [deserializeBackpressure] from commonMain. These tests verify the full
 * PersistedObservation roundtrip through this shared contract, ensuring
 * consistent behaviour regardless of which platform serializes the data.
 */
@OptIn(ExperimentalUuidApi::class)
class ObservationPersistenceRoundtripTest {
    private val serviceUuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val charUuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")

    // --- PersistedObservation to/from map format ---

    @Test
    fun persistedObservationRoundtripWithLatestBackpressure() {
        val original =
            PersistedObservation(
                ObservationKey(serviceUuid, charUuid),
                BackpressureStrategy.Latest,
            )
        val restored = roundtrip(original)
        assertEquals(original, restored)
    }

    @Test
    fun persistedObservationRoundtripWithUnboundedBackpressure() {
        val original =
            PersistedObservation(
                ObservationKey(serviceUuid, charUuid),
                BackpressureStrategy.Unbounded,
            )
        val restored = roundtrip(original)
        assertEquals(original, restored)
    }

    @Test
    fun persistedObservationRoundtripWithBufferBackpressure() {
        val original =
            PersistedObservation(
                ObservationKey(serviceUuid, charUuid),
                BackpressureStrategy.Buffer(64),
            )
        val restored = roundtrip(original)
        assertEquals(original, restored)
        assertTrue(restored.backpressure is BackpressureStrategy.Buffer)
        assertEquals(64, (restored.backpressure as BackpressureStrategy.Buffer).capacity)
    }

    @Test
    fun persistedObservationRoundtripWithLargeBufferCapacity() {
        val original =
            PersistedObservation(
                ObservationKey(serviceUuid, charUuid),
                BackpressureStrategy.Buffer(4096),
            )
        val restored = roundtrip(original)
        assertEquals(4096, (restored.backpressure as BackpressureStrategy.Buffer).capacity)
    }

    @Test
    fun multiplePersistedObservationsRoundtrip() {
        val originals =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
                PersistedObservation(
                    ObservationKey(
                        serviceUuid,
                        Uuid.parse("00002a38-0000-1000-8000-00805f9b34fb"),
                    ),
                    BackpressureStrategy.Buffer(32),
                ),
                PersistedObservation(
                    ObservationKey(
                        Uuid.parse("0000180e-0000-1000-8000-00805f9b34fb"),
                        Uuid.parse("00002a39-0000-1000-8000-00805f9b34fb"),
                    ),
                    BackpressureStrategy.Unbounded,
                ),
            )

        val restored = originals.map { roundtrip(it) }.toSet()
        assertEquals(originals, restored)
    }

    // --- ObservationKey roundtrip ---

    @Test
    fun observationKeyRoundtripThroughUuidParsing() {
        val original = ObservationKey(serviceUuid, charUuid)
        val serviceStr = original.serviceUuid.toString()
        val charStr = original.charUuid.toString()

        // Reconstruct from strings (as both platform impls do)
        val restored =
            ObservationKey(
                serviceUuid = Uuid.parse(serviceStr),
                charUuid = Uuid.parse(charStr),
            )

        assertEquals(original, restored)
    }

    @Test
    fun observationKeyEqualityIsValueBased() {
        val key1 = ObservationKey(serviceUuid, charUuid)
        val key2 =
            ObservationKey(
                Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
                Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb"),
            )
        assertEquals(key1, key2)
        assertEquals(key1.hashCode(), key2.hashCode())
    }

    // --- Backpressure format stability ---

    @Test
    fun serializedBackpressureMapEntryIsConsistent() {
        // Verify the bp field format that both platform impls depend on
        val bpLatest = serializeBackpressure(BackpressureStrategy.Latest)
        val bpUnbounded = serializeBackpressure(BackpressureStrategy.Unbounded)
        val bpBuffer = serializeBackpressure(BackpressureStrategy.Buffer(128))

        // All serialized forms must be non-empty simple strings
        assertTrue(bpLatest.isNotEmpty())
        assertTrue(bpUnbounded.isNotEmpty())
        assertTrue(bpBuffer.isNotEmpty())

        // Deserialization must be exact inverse
        assertEquals(BackpressureStrategy.Latest, deserializeBackpressure(bpLatest))
        assertEquals(BackpressureStrategy.Unbounded, deserializeBackpressure(bpUnbounded))
        assertEquals(BackpressureStrategy.Buffer(128), deserializeBackpressure(bpBuffer))
    }

    // --- Edge cases ---

    @Test
    fun missingBackpressureFieldDefaultsToLatest() {
        // Simulate a map entry without the "bp" key (platform data migration)
        val key = ObservationKey(serviceUuid, charUuid)
        val restored =
            buildPersistedObservation(
                serviceStr = serviceUuid.toString(),
                charStr = charUuid.toString(),
                bpStr = null, // Missing bp field
            )
        assertEquals(key, restored.key)
        assertEquals(BackpressureStrategy.Latest, restored.backpressure)
    }

    @Test
    fun unknownBackpressureFieldValueDefaultsToLatest() {
        val key = ObservationKey(serviceUuid, charUuid)
        val restored =
            buildPersistedObservation(
                serviceStr = serviceUuid.toString(),
                charStr = charUuid.toString(),
                bpStr = "garbage_value",
            )
        assertEquals(key, restored.key)
        assertEquals(BackpressureStrategy.Latest, restored.backpressure)
    }

    @Test
    fun emptyBackpressureFieldValueDefaultsToLatest() {
        val key = ObservationKey(serviceUuid, charUuid)
        val restored =
            buildPersistedObservation(
                serviceStr = serviceUuid.toString(),
                charStr = charUuid.toString(),
                bpStr = "",
            )
        assertEquals(key, restored.key)
        assertEquals(BackpressureStrategy.Latest, restored.backpressure)
    }

    @Test
    fun observationKeyOrderDoesNotAffectEquality() {
        // The ObservationKey uses (serviceUuid, charUuid) pair; verify
        // different services with same charUuid produce different keys
        val keyA = ObservationKey(serviceUuid, charUuid)
        val keyB =
            ObservationKey(
                Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb"),
                charUuid,
            )
        assertTrue(keyA != keyB)
    }

    // --- Helpers ---

    /**
     * Convert a [PersistedObservation] to the map format used by both platforms,
     * then reconstruct it. Simulates the shared serialization contract between
     * Android (SharedPreferences) and iOS (NSUserDefaults).
     */
    private fun roundtrip(obs: PersistedObservation): PersistedObservation =
        buildPersistedObservation(
            serviceStr = obs.key.serviceUuid.toString(),
            charStr = obs.key.charUuid.toString(),
            bpStr = serializeBackpressure(obs.backpressure),
        )

    /**
     * Build a [PersistedObservation] from the three map-format fields.
     * Mirrors the deserialization logic shared by both Android and iOS
     * platform implementations.
     */
    private fun buildPersistedObservation(
        serviceStr: String,
        charStr: String,
        bpStr: String?,
    ): PersistedObservation =
        PersistedObservation(
            key =
                ObservationKey(
                    serviceUuid = Uuid.parse(serviceStr),
                    charUuid = Uuid.parse(charStr),
                ),
            backpressure = deserializeBackpressure(bpStr),
        )
}
