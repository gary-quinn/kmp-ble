package com.atruedev.kmpble.server

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.atruedev.kmpble.gatt.internal.CCCD_UUID
import com.atruedev.kmpble.scanner.uuidFrom
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid

/**
 * Validates that Android [BluetoothGattService] and [BluetoothGattCharacteristic]
 * can be constructed on the real Android runtime with the same patterns used by
 * [AndroidGattServer.buildNativeService].
 *
 * Host tests cannot create these types because their constructors depend on
 * internal Android runtime state.
 */
@OptIn(ExperimentalUuidApi::class)
@RunWith(AndroidJUnit4::class)
class GattServerBuilderIntegrationTest {
    private val serviceUuid = uuidFrom("180d")
    private val charUuid = uuidFrom("2a37")
    private val descriptorUuid = uuidFrom("2901")

    @Test
    fun nativeService_canBeConstructed() {
        val service =
            BluetoothGattService(
                serviceUuid.toJavaUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY,
            )
        assertNotNull(service)
        assertEquals(serviceUuid.toJavaUuid(), service.uuid)
        assertEquals(BluetoothGattService.SERVICE_TYPE_PRIMARY, service.type)
    }

    @Test
    fun nativeCharacteristic_canBeConstructed_withProperties() {
        val props =
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY
        val perms = BluetoothGattCharacteristic.PERMISSION_READ

        val char = BluetoothGattCharacteristic(charUuid.toJavaUuid(), props, perms)
        assertNotNull(char)
        assertEquals(charUuid.toJavaUuid(), char.uuid)
        assertEquals(props, char.properties)
        assertEquals(perms, char.permissions)
    }

    @Test
    fun nativeCharacteristic_withCccd_hasDescriptor() {
        val props = BluetoothGattCharacteristic.PROPERTY_NOTIFY
        val char =
            BluetoothGattCharacteristic(
                charUuid.toJavaUuid(),
                props,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )

        val cccd =
            BluetoothGattDescriptor(
                CCCD_UUID.toJavaUuid(),
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            )
        char.addDescriptor(cccd)

        val foundCccd = char.getDescriptor(CCCD_UUID.toJavaUuid())
        assertNotNull(foundCccd, "CCCD descriptor should be retrievable after adding")
    }

    @Test
    fun nativeService_withCharacteristic_isAccessible() {
        val service =
            BluetoothGattService(
                serviceUuid.toJavaUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY,
            )

        val char =
            BluetoothGattCharacteristic(
                charUuid.toJavaUuid(),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
        service.addCharacteristic(char)

        val found = service.getCharacteristic(charUuid.toJavaUuid())
        assertNotNull(found, "Characteristic should be retrievable from service")
        assertEquals(charUuid.toJavaUuid(), found.uuid)
    }

    @Test
    fun nativeService_withMultipleCharacteristics_allAccessible() {
        val service =
            BluetoothGattService(
                serviceUuid.toJavaUuid(),
                BluetoothGattService.SERVICE_TYPE_PRIMARY,
            )

        val char1 =
            BluetoothGattCharacteristic(
                charUuid.toJavaUuid(),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )

        val char2Uuid = uuidFrom("2a38")
        val char2 =
            BluetoothGattCharacteristic(
                char2Uuid.toJavaUuid(),
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )

        service.addCharacteristic(char1)
        service.addCharacteristic(char2)

        assertTrue(service.characteristics.size == 2)
    }

    @Test
    fun nativeCharacteristic_withUserDescriptor_isAccessible() {
        val char =
            BluetoothGattCharacteristic(
                charUuid.toJavaUuid(),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )

        val descriptor =
            BluetoothGattDescriptor(
                descriptorUuid.toJavaUuid(),
                BluetoothGattDescriptor.PERMISSION_READ,
            )
        char.addDescriptor(descriptor)

        val found = char.getDescriptor(descriptorUuid.toJavaUuid())
        assertNotNull(found)
        assertEquals(descriptorUuid.toJavaUuid(), found.uuid)
    }
}
