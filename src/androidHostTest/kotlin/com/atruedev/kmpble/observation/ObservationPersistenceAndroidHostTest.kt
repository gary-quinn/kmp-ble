package com.atruedev.kmpble.observation

import android.content.Context
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.internal.ObservationKey
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests for ObservationPersistence SharedPreferences roundtrip.
 *
 * Covers:
 * - BackpressureStrategy serialization/deserialization
 * - JsonArrayEncoder encode/decode
 * - Edge cases (corrupt data, empty IDs, large capacities)
 * - Multiple save/restore cycles
 * - UUID parsing robustness
 */
@OptIn(ExperimentalUuidApi::class)
@RunWith(RobolectricTestRunner::class)
class ObservationPersistenceAndroidHostTest {

    private lateinit var appContext: Context
    private val peripheralId = "00:11:22:33:44:55"

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        ObservationPersistence.context = appContext
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    // --- BackpressureStrategy Serialization/Deserialization ---

    @Test
    fun serializeBackpressureLatest() {
        val result = serializeBackpressure(BackpressureStrategy.Latest)
        assertEquals("latest", result)
    }

    @Test
    fun serializeBackpressureBuffer() {
        val result = serializeBackpressure(BackpressureStrategy.Buffer(64))
        assertEquals("buffer:64", result)
    }

    @Test
    fun serializeBackpressureUnbounded() {
        val result = serializeBackpressure(BackpressureStrategy.Unbounded)
        assertEquals("unbounded", result)
    }

    @Test
    fun deserializeBackpressureLatest() {
        val result = deserializeBackpressure("latest")
        assertEquals(BackpressureStrategy.Latest, result)
    }

    @Test
    fun deserializeBackpressureBuffer() {
        val result = deserializeBackpressure("buffer:128")
        assertEquals(BackpressureStrategy.Buffer(128), result)
    }

    @Test
    fun deserializeBackpressureUnbounded() {
        val result = deserializeBackpressure("unbounded")
        assertEquals(BackpressureStrategy.Unbounded, result)
    }

    @Test
    fun deserializeBackpressureNull() {
        val result = deserializeBackpressure(null)
        assertEquals(BackpressureStrategy.Latest, result)
    }

    @Test
    fun deserializeBackpressureUnknown() {
        val result = deserializeBackpressure("unknown")
        assertEquals(BackpressureStrategy.Latest, result)
    }

    @Test
    fun deserializeBackpressureBufferInvalidCapacity() {
        val result = deserializeBackpressure("buffer:invalid")
        assertEquals(BackpressureStrategy.Buffer(64), result)
    }

    // --- JsonArrayEncoder ---

    @Test
    fun encodeEmptyList() {
        val result = JsonArrayEncoder.encode(emptyList())
        assertEquals("[]", result)
    }

    @Test
    fun encodeSingleEntry() {
        val entries = listOf(
            mapOf(
                "s" to "0000180d-0000-1000-8000-00805f9b34fb",
                "c" to "00002a37-0000-1000-8000-00805f9b34fb",
                "bp" to "latest"
            )
        )
        val result = JsonArrayEncoder.encode(entries)
        assertTrue(result.contains("\"s\""))
        assertTrue(result.contains("\"c\""))
        assertTrue(result.contains("\"bp\""))
        assertTrue(result.contains("latest"))
    }

    @Test
    fun encodeMultipleEntries() {
        val entries = listOf(
            mapOf(
                "s" to "0000180d-0000-1000-8000-00805f9b34fb",
                "c" to "00002a37-0000-1000-8000-00805f9b34fb",
                "bp" to "latest"
            ),
            mapOf(
                "s" to "0000180d-0000-1000-8000-00805f9b34fb",
                "c" to "00002a38-0000-1000-8000-00805f9b34fb",
                "bp" to "buffer:32"
            )
        )
        val result = JsonArrayEncoder.encode(entries)
        assertTrue(result.contains("latest"))
        assertTrue(result.contains("buffer:32"))
    }

    @Test
    fun decodeEmptyArray() {
        val result = JsonArrayEncoder.decode("[]")
        assertEquals(emptyList(), result)
    }

    @Test
    fun decodeSingleEntry() {
        val json = """[{"s":"0000180d-0000-1000-8000-00805f9b34fb","c":"00002a37-0000-1000-8000-00805f9b34fb","bp":"latest"}]"""
        val result = JsonArrayEncoder.decode(json)
        assertEquals(1, result.size)
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", result[0]["s"])
        assertEquals("00002a37-0000-1000-8000-00805f9b34fb", result[0]["c"])
        assertEquals("latest", result[0]["bp"])
    }

    @Test
    fun decodeInvalidJson() {
        val result = JsonArrayEncoder.decode("not json")
        assertEquals(emptyList(), result)
    }

    @Test
    fun encodeDecodeRoundtrip() {
        val entries = listOf(
            mapOf(
                "s" to "0000180d-0000-1000-8000-00805f9b34fb",
                "c" to "00002a37-0000-1000-8000-00805f9b34fb",
                "bp" to "buffer:128"
            ),
            mapOf(
                "s" to "0000180f-0000-1000-8000-00805f9b34fb",
                "c" to "00002a19-0000-1000-8000-00805f9b34fb",
                "bp" to "unbounded"
            )
        )
        val encoded = JsonArrayEncoder.encode(entries)
        val decoded = JsonArrayEncoder.decode(encoded)
        assertEquals(entries.size, decoded.size)
        assertEquals(entries[0]["s"], decoded[0]["s"])
        assertEquals(entries[0]["c"], decoded[0]["c"])
        assertEquals(entries[0]["bp"], decoded[0]["bp"])
        assertEquals(entries[1]["s"], decoded[1]["s"])
    }

    // --- ObservationPersistence ---

    @Test
    fun restoreBeforeSaveReturnsEmptySet() {
        val persistence = ObservationPersistence()
        val restored = persistence.restore("nonexistent:device")
        assertEquals(emptySet(), restored)
    }

    @Test
    fun saveThenClearThenSaveAgain() {
        val persistence = ObservationPersistence()
        val observations = createObservations()
        
        persistence.save(peripheralId, observations)
        assertEquals(2, persistence.restore(peripheralId).size)
        
        persistence.clear(peripheralId)
        assertEquals(emptySet(), persistence.restore(peripheralId))
        
        val newObservations = createObservations(count = 1)
        persistence.save(peripheralId, newObservations)
        assertEquals(1, persistence.restore(peripheralId).size)
    }

    @Test
    fun multipleSaveRestoreCycles() {
        val persistence = ObservationPersistence()
        
        // First save/restore
        persistence.save(peripheralId, createObservations())
        var restored = persistence.restore(peripheralId)
        assertEquals(2, restored.size)
        
        // Second save/restore
        persistence.save(peripheralId, createObservations(count = 3))
        restored = persistence.restore(peripheralId)
        assertEquals(3, restored.size)
        
        // Third save/restore with empty
        persistence.save(peripheralId, emptySet())
        restored = persistence.restore(peripheralId)
        assertEquals(emptySet(), restored)
    }

    @Test
    fun bufferCapacityZero() {
        val persistence = ObservationPersistence()
        val observations = setOf(
            PersistedObservation(
                ObservationKey(
                    Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
                ),
                BackpressureStrategy.Buffer(0)
            )
        )
        
        persistence.save(peripheralId, observations)
        val restored = persistence.restore(peripheralId)
        
        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Buffer(0), restored.first().backpressure)
    }

    @Test
    fun bufferCapacityLarge() {
        val persistence = ObservationPersistence()
        val maxCapacity = Int.MAX_VALUE
        
        val observations = setOf(
            PersistedObservation(
                ObservationKey(
                    Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
                ),
                BackpressureStrategy.Buffer(maxCapacity)
            )
        )
        
        persistence.save(peripheralId, observations)
        val restored = persistence.restore(peripheralId)
        
        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Buffer(maxCapacity), restored.first().backpressure)
    }

    @Test
    fun truncatedJsonArray() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().putString("obs.$peripheralId", "[{\"s\":\"test\"").commit()
        
        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        assertEquals(emptySet(), restored)
    }

    @Test
    fun jsonWithExtraFields() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val json = """[{"s":"0000180d-0000-1000-8000-00805f9b34fb","c":"00002a37-0000-1000-8000-00805f9b34fb","bp":"latest","extra":"data"}]"""
        prefs.edit().putString("obs.$peripheralId", json).commit()
        
        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        
        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Latest, restored.first().backpressure)
    }

    @Test
    fun invalidUuidFormat() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val json = """[{"s":"not-a-uuid","c":"also-not-uuid","bp":"latest"}]"""
        prefs.edit().putString("obs.$peripheralId", json).commit()
        
        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        
        assertEquals(emptySet(), restored, "Invalid UUIDs should be skipped")
    }

    @Test
    fun missingServiceField() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val json = """[{"c":"00002a37-0000-1000-8000-00805f9b34fb","bp":"latest"}]"""
        prefs.edit().putString("obs.$peripheralId", json).commit()
        
        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        
        assertEquals(emptySet(), restored)
    }

    @Test
    fun missingCharField() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val json = """[{"s":"0000180d-0000-1000-8000-00805f9b34fb","bp":"latest"}]"""
        prefs.edit().putString("obs.$peripheralId", json).commit()
        
        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        
        assertEquals(emptySet(), restored)
    }

    @Test
    fun differentPeripheralIdsAreIndependent() {
        val persistence = ObservationPersistence()
        
        persistence.save("11:22:33:44:55:66", createObservations())
        persistence.save("aa:bb:cc:dd:ee:ff", createObservations(count = 3))
        
        assertEquals(2, persistence.restore("11:22:33:44:55:66").size)
        assertEquals(3, persistence.restore("aa:bb:cc:dd:ee:ff").size)
        
        persistence.clear("11:22:33:44:55:66")
        
        assertEquals(emptySet(), persistence.restore("11:22:33:44:55:66"))
        assertEquals(3, persistence.restore("aa:bb:cc:dd:ee:ff").size)
    }

    @Test
    fun peripheralIdWithSpecialCharacters() {
        val persistence = ObservationPersistence()
        val specialId = "test-peripheral-123"
        
        persistence.save(specialId, createObservations())
        val restored = persistence.restore(specialId)
        
        assertEquals(2, restored.size)
    }

    @Test
    fun emptyPeripheralId() {
        val persistence = ObservationPersistence()
        
        persistence.save("", createObservations())
        val restored = persistence.restore("")
        
        assertEquals(2, restored.size)
    }

    @Test
    fun largeNumberOfObservations() {
        val persistence = ObservationPersistence()
        val largeSet = (1..100).map { index ->
            PersistedObservation(
                ObservationKey(
                    Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb"),
                    Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
                ),
                BackpressureStrategy.Latest
            )
        }.toSet()
        
        persistence.save(peripheralId, largeSet)
        val restored = persistence.restore(peripheralId)
        
        assertEquals(100, restored.size)
    }

    @Test
    fun saveThenRestoreTwice() {
        val persistence = ObservationPersistence()
        
        persistence.save(peripheralId, createObservations())
        val firstRestore = persistence.restore(peripheralId)
        assertEquals(2, firstRestore.size)
        
        val secondRestore = persistence.restore(peripheralId)
        assertEquals(2, secondRestore.size)
    }

    @Test
    fun clearDoesNotAffectOtherPeripherals() {
        val persistence = ObservationPersistence()
        
        persistence.save("peripheral1", createObservations())
        persistence.save("peripheral2", createObservations(count = 3))
        
        persistence.clear("peripheral1")
        
        assertEquals(emptySet(), persistence.restore("peripheral1"))
        assertEquals(3, persistence.restore("peripheral2").size)
    }

    // --- Helper Functions ---

    private fun createObservations(count: Int = 2): Set<PersistedObservation> {
        return (1..count).map { index ->
            val serviceUuid = if (index == 1) {
                Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
            } else {
                Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb")
            }
            
            val charUuid = when (index) {
                1 -> Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
                2 -> Uuid.parse("00002a38-0000-1000-8000-00805f9b34fb")
                else -> Uuid.parse("00002a19-0000-1000-8000-00805f9b34fb")
            }
            
            val backpressure = when (index % 3) {
                0 -> BackpressureStrategy.Latest
                1 -> BackpressureStrategy.Buffer(32)
                2 -> BackpressureStrategy.Unbounded
                else -> BackpressureStrategy.Latest
            }
            
            PersistedObservation(
                ObservationKey(serviceUuid, charUuid),
                backpressure
            )
        }.toSet()
    }
}
