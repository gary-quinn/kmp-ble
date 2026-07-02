package com.atruedev.kmpble.observation

import android.content.Context
import com.atruedev.kmpble.gatt.BackpressureStrategy
import com.atruedev.kmpble.gatt.internal.JsonArrayEncoder
import com.atruedev.kmpble.gatt.internal.ObservationKey
import com.atruedev.kmpble.gatt.internal.ObservationPersistence
import com.atruedev.kmpble.gatt.internal.PersistedObservation
import com.atruedev.kmpble.gatt.internal.deserializeBackpressure
import com.atruedev.kmpble.gatt.internal.serializeBackpressure
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Tests that ObservationPersistence correctly saves, restores, and clears
 * observation state through SharedPreferences on Android, exercising the
 * JsonArrayEncoder encode/decode roundtrip.
 *
 * These tests complement the jvmTest suite which only covers the JVM
 * in-memory implementation.
 */
@OptIn(ExperimentalUuidApi::class)
@RunWith(RobolectricTestRunner::class)
class ObservationPersistenceAndroidHostTest {
    private lateinit var appContext: Context
    private val peripheralId = "00:11:22:33:44:55"
    private val serviceUuid = Uuid.parse("0000180d-0000-1000-8000-00805f9b34fb")
    private val charUuid = Uuid.parse("00002a37-0000-1000-8000-00805f9b34fb")
    private val charUuid2 = Uuid.parse("00002a38-0000-1000-8000-00805f9b34fb")

    @Before
    fun setup() {
        appContext = RuntimeEnvironment.getApplication()
        ObservationPersistence.context = appContext
        // Clear any persisted state from previous tests
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun saveAndRestoreThroughSharedPreferencesPreservesAllFields() {
        val persistence = ObservationPersistence()
        val observations =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid2),
                    BackpressureStrategy.Buffer(32),
                ),
            )

        persistence.save(peripheralId, observations)

        // Verify JSON was written to SharedPreferences
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("obs.$peripheralId", null)
        assertEquals(true, rawJson != null, "SharedPreferences should contain serialized JSON")
        assertEquals(true, rawJson!!.contains("\"latest\""), "JSON should contain 'latest'")
        assertEquals(true, rawJson.contains("\"buffer:32\""), "JSON should contain 'buffer:32'")

        // Restore and verify all fields survived the JSON roundtrip
        val restored = persistence.restore(peripheralId)
        assertEquals(2, restored.size)
        val byKey = restored.associateBy { it.key }
        assertEquals(
            BackpressureStrategy.Latest,
            byKey[ObservationKey(serviceUuid, charUuid)]?.backpressure,
        )
        assertEquals(
            BackpressureStrategy.Buffer(32),
            byKey[ObservationKey(serviceUuid, charUuid2)]?.backpressure,
        )
    }

    @Test
    fun restoreReturnsEmptyForUnknownPeripheral() {
        val persistence = ObservationPersistence()
        assertEquals(emptySet(), persistence.restore("unknown:device"))
    }

    @Test
    fun clearRemovesSharedPreferencesEntry() {
        val persistence = ObservationPersistence()
        val observations =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
            )

        persistence.save(peripheralId, observations)
        assertEquals(1, persistence.restore(peripheralId).size)

        persistence.clear(peripheralId)
        assertEquals(emptySet(), persistence.restore(peripheralId))

        // Verify SharedPreferences entry is actually removed
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        assertEquals(null, prefs.getString("obs.$peripheralId", null))
    }

    @Test
    fun saveEmptySetClearsSharedPreferencesEntry() {
        val persistence = ObservationPersistence()
        persistence.save(
            peripheralId,
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
            ),
        )
        assertEquals(1, persistence.restore(peripheralId).size)

        persistence.save(peripheralId, emptySet())
        assertEquals(emptySet(), persistence.restore(peripheralId))

        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        assertEquals(null, prefs.getString("obs.$peripheralId", null))
    }

    @Test
    fun saveUnboundedBackpressureSurvivesRoundtrip() {
        val persistence = ObservationPersistence()
        val observations =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Unbounded,
                ),
            )

        persistence.save(peripheralId, observations)
        val restored = persistence.restore(peripheralId)

        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Unbounded, restored.first().backpressure)

        // Verify raw JSON encoding
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("obs.$peripheralId", null)
        assertEquals(true, rawJson!!.contains("\"unbounded\""))
    }

    @Test
    fun bufferBackpressureWithCustomCapacitySurvivesRoundtrip() {
        val persistence = ObservationPersistence()
        val observations =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Buffer(128),
                ),
            )

        persistence.save(peripheralId, observations)
        val restored = persistence.restore(peripheralId)

        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Buffer(128), restored.first().backpressure)

        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val rawJson = prefs.getString("obs.$peripheralId", null)
        assertEquals(true, rawJson!!.contains("\"buffer:128\""))
    }

    @Test
    fun multiplePeripheralsHaveIndependentSharedPreferencesState() {
        val persistence = ObservationPersistence()
        val id1 = "AA:BB:CC:DD:EE:FF"
        val id2 = "11:22:33:44:55:66"

        persistence.save(
            id1,
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
            ),
        )
        persistence.save(
            id2,
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid2),
                    BackpressureStrategy.Buffer(16),
                ),
            ),
        )

        // Both peripherals should restore correctly
        assertEquals(1, persistence.restore(id1).size)
        assertEquals(1, persistence.restore(id2).size)
        assertEquals(BackpressureStrategy.Latest, persistence.restore(id1).first().backpressure)
        assertEquals(BackpressureStrategy.Buffer(16), persistence.restore(id2).first().backpressure)

        // Clear one peripheral - other should be unaffected
        persistence.clear(id1)
        assertEquals(emptySet(), persistence.restore(id1))
        assertEquals(1, persistence.restore(id2).size)

        // Verify SharedPreferences keys are independent
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        assertEquals(null, prefs.getString("obs.$id1", null))
        assertEquals(true, prefs.getString("obs.$id2", null) != null)
    }

    @Test
    fun restoreFromCorruptJsonReturnsEmptySet() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().putString("obs.$peripheralId", "not valid json at all").commit()

        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        assertEquals(emptySet(), restored, "Corrupt JSON should yield empty set")
    }

    @Test
    fun restoreFromEmptyJsonArrayReturnsEmptySet() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().putString("obs.$peripheralId", "[]").commit()

        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        assertEquals(emptySet(), restored, "Empty JSON array should yield empty set")
    }

    @Test
    fun restoreWithMissingBackpressureFieldDefaultsToLatest() {
        // Write JSON without the "bp" field (simulating pre-strategy data)
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val jsonWithoutBp = """[{"s":"$serviceUuid","c":"$charUuid"}]"""
        prefs.edit().putString("obs.$peripheralId", jsonWithoutBp).commit()

        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Latest, restored.first().backpressure)
    }

    // --- Direct BackpressureStrategy serialization/deserialization ---

    @Test
    fun serializeLatestBackpressureToLatestString() {
        assertEquals("latest", serializeBackpressure(BackpressureStrategy.Latest))
    }

    @Test
    fun serializeBufferBackpressureToBufferFormat() {
        assertEquals("buffer:64", serializeBackpressure(BackpressureStrategy.Buffer(64)))
    }

    @Test
    fun serializeUnboundedBackpressureToUnboundedString() {
        assertEquals("unbounded", serializeBackpressure(BackpressureStrategy.Unbounded))
    }

    @Test
    fun deserializeLatestFromString() {
        assertEquals(BackpressureStrategy.Latest, deserializeBackpressure("latest"))
    }

    @Test
    fun deserializeBufferFromString() {
        assertEquals(BackpressureStrategy.Buffer(128), deserializeBackpressure("buffer:128"))
    }

    @Test
    fun deserializeUnboundedFromString() {
        assertEquals(BackpressureStrategy.Unbounded, deserializeBackpressure("unbounded"))
    }

    @Test
    fun deserializeNullDefaultsToLatest() {
        assertEquals(BackpressureStrategy.Latest, deserializeBackpressure(null))
    }

    @Test
    fun deserializeUnknownDefaultsToLatest() {
        assertEquals(BackpressureStrategy.Latest, deserializeBackpressure("garbage"))
    }

    @Test
    fun deserializeBufferWithNonNumericCapacityUsesDefault64() {
        assertEquals(
            BackpressureStrategy.Buffer(64),
            deserializeBackpressure("buffer:notanumber"),
        )
    }

    // --- Direct JsonArrayEncoder encode/decode ---

    @Test
    fun encodeEmptyListProducesEmptyArray() {
        assertEquals("[]", JsonArrayEncoder.encode(emptyList()))
    }

    @Test
    fun encodeSingleEntryProducesValidJson() {
        val entries =
            listOf(
                mapOf(
                    "s" to serviceUuid.toString(),
                    "c" to charUuid.toString(),
                    "bp" to "latest",
                ),
            )
        val json = JsonArrayEncoder.encode(entries)
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("\"s\""))
        assertTrue(json.contains("\"c\""))
        assertTrue(json.contains("\"bp\""))
        assertTrue(json.contains("latest"))
    }

    @Test
    fun encodeMultipleEntriesProducesValidJson() {
        val entries =
            listOf(
                mapOf("s" to serviceUuid.toString(), "c" to charUuid.toString(), "bp" to "latest"),
                mapOf("s" to serviceUuid.toString(), "c" to charUuid2.toString(), "bp" to "buffer:32"),
            )
        val json = JsonArrayEncoder.encode(entries)
        assertTrue(json.contains("latest"))
        assertTrue(json.contains("buffer:32"))
    }

    @Test
    fun decodeEmptyArrayProducesEmptyList() {
        val result = JsonArrayEncoder.decode("[]")
        assertTrue(result.isEmpty())
    }

    @Test
    fun decodeSingleEntryWithAllFields() {
        val json = """[{"s":"$serviceUuid","c":"$charUuid","bp":"latest"}]"""
        val result = JsonArrayEncoder.decode(json)
        assertEquals(1, result.size)
        assertEquals(serviceUuid.toString(), result[0]["s"])
        assertEquals(charUuid.toString(), result[0]["c"])
        assertEquals("latest", result[0]["bp"])
    }

    @Test
    fun decodeInvalidJsonProducesEmptyList() {
        val result = JsonArrayEncoder.decode("not json at all")
        assertTrue(result.isEmpty())
    }

    @Test
    fun encodeThenDecodeRoundtripPreservesAllEntries() {
        val entries =
            listOf(
                mapOf(
                    "s" to serviceUuid.toString(),
                    "c" to charUuid.toString(),
                    "bp" to "buffer:128",
                ),
                mapOf(
                    "s" to Uuid.parse("0000180f-0000-1000-8000-00805f9b34fb").toString(),
                    "c" to Uuid.parse("00002a19-0000-1000-8000-00805f9b34fb").toString(),
                    "bp" to "unbounded",
                ),
            )
        val encoded = JsonArrayEncoder.encode(entries)
        val decoded = JsonArrayEncoder.decode(encoded)
        assertEquals(entries.size, decoded.size)
        entries.forEachIndexed { index, expected ->
            assertEquals(expected["s"], decoded[index]["s"])
            assertEquals(expected["c"], decoded[index]["c"])
            assertEquals(expected["bp"], decoded[index]["bp"])
        }
    }

    // --- Edge case tests ---

    @Test
    fun bufferZeroCapacitySurvivesRoundtrip() {
        val persistence = ObservationPersistence()
        val observations =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Buffer(0),
                ),
            )
        persistence.save(peripheralId, observations)
        val restored = persistence.restore(peripheralId)

        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Buffer(0), restored.first().backpressure)
    }

    @Test
    fun bufferMaxCapacitySurvivesRoundtrip() {
        val persistence = ObservationPersistence()
        val observations =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Buffer(Int.MAX_VALUE),
                ),
            )
        persistence.save(peripheralId, observations)
        val restored = persistence.restore(peripheralId)

        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Buffer(Int.MAX_VALUE), restored.first().backpressure)
    }

    @Test
    fun truncatedJsonArrayProducesEmptySet() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().putString("obs.$peripheralId", """[{"s":"test""").commit()

        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun jsonWithExtraFieldsStillDecodesCorrectly() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        val json =
            """[{"s":"$serviceUuid","c":"$charUuid","bp":"latest","extra":"should be ignored"}]"""
        prefs.edit().putString("obs.$peripheralId", json).commit()

        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)

        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Latest, restored.first().backpressure)
    }

    @Test
    fun invalidUuidFormatIsSkipped() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().putString("obs.$peripheralId", """[{"s":"not-a-uuid","c":"also-bad","bp":"latest"}]""").commit()

        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        assertTrue(restored.isEmpty(), "Invalid UUIDs should be skipped")
    }

    @Test
    fun missingServiceFieldSkipsEntry() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().putString("obs.$peripheralId", """[{"c":"$charUuid","bp":"latest"}]""").commit()

        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun missingCharFieldSkipsEntry() {
        val prefs = appContext.getSharedPreferences("com.atruedev.kmpble.cccd", Context.MODE_PRIVATE)
        prefs.edit().putString("obs.$peripheralId", """[{"s":"$serviceUuid","bp":"latest"}]""").commit()

        val persistence = ObservationPersistence()
        val restored = persistence.restore(peripheralId)
        assertTrue(restored.isEmpty())
    }

    @Test
    fun specialCharactersInPeripheralId() {
        val persistence = ObservationPersistence()
        val specialId = "test-peripheral-with-dashes"

        persistence.save(
            specialId,
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
            ),
        )
        val restored = persistence.restore(specialId)
        assertEquals(1, restored.size)
    }

    @Test
    fun emptyPeripheralIdWorks() {
        val persistence = ObservationPersistence()
        persistence.save(
            "",
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
            ),
        )
        val restored = persistence.restore("")
        assertEquals(1, restored.size)
    }

    @Test
    fun largeNumberOfObservationsRoundtripsCorrectly() {
        val persistence = ObservationPersistence()
        val observations =
            (1..100)
                .map { index ->
                    val charUuid = Uuid.fromLongs(0, index.toLong())
                    PersistedObservation(
                        ObservationKey(serviceUuid, charUuid),
                        BackpressureStrategy.Latest,
                    )
                }.toSet()

        persistence.save(peripheralId, observations)
        val restored = persistence.restore(peripheralId)
        assertEquals(100, restored.size)
    }

    @Test
    fun saveThenRestoreTwiceReturnsSameData() {
        val persistence = ObservationPersistence()
        persistence.save(
            peripheralId,
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
            ),
        )

        val first = persistence.restore(peripheralId)
        assertEquals(1, first.size)

        val second = persistence.restore(peripheralId)
        assertEquals(1, second.size)
    }

    @Test
    fun saveOverwriteUpdatesPersistedState() {
        val persistence = ObservationPersistence()
        val original =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid),
                    BackpressureStrategy.Latest,
                ),
            )
        persistence.save(peripheralId, original)
        assertEquals(1, persistence.restore(peripheralId).size)

        val updated =
            setOf(
                PersistedObservation(
                    ObservationKey(serviceUuid, charUuid2),
                    BackpressureStrategy.Buffer(64),
                ),
            )
        persistence.save(peripheralId, updated)
        val restored = persistence.restore(peripheralId)
        assertEquals(1, restored.size)
        assertEquals(BackpressureStrategy.Buffer(64), restored.first().backpressure)
    }
}
