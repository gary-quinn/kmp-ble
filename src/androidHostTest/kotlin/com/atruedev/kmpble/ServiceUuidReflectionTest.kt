package com.atruedev.kmpble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * JVM reflection test that verifies [ServiceUuid.ALL] contains every Uuid property
 * defined in the object. Catches omissions when new UUIDs are added but forgotten
 * in the ALL list.
 */
@OptIn(ExperimentalUuidApi::class)
class ServiceUuidReflectionTest {
    @Test
    fun allListContainsEveryUuidProperty() {
        val uuidProperties =
            ServiceUuid::class.java.declaredFields
                .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .filter { Uuid::class.java.isAssignableFrom(it.type) }
                .map { it.name }
                .sorted()

        val allUuids = ServiceUuid.ALL
        assertEquals(
            uuidProperties.size,
            allUuids.size,
            "ServiceUuid.ALL (${allUuids.size}) is out of sync with declared " +
                "Uuid properties (${uuidProperties.size}). Properties: $uuidProperties",
        )
    }
}
