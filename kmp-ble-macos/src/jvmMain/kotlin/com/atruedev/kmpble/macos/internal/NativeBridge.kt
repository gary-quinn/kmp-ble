package com.atruedev.kmpble.macos.internal

/** Receives every CoreBluetooth callback from the native shim; see [MacosEvent.decode]. */
internal fun interface NativeCallback {
    fun onEvent(
        kind: Int,
        a: Long,
        b: Long,
        status: Int,
        text: String?,
        data: ByteArray?,
    )
}

/** JNI entry points of `libkmpble_macos.dylib` (`src/native/KmpBleMacos.m`). */
internal object NativeBridge {
    @JvmStatic external fun nativeInit(callback: NativeCallback): Int

    @JvmStatic external fun nativeMissingUsageDescriptionHost(): String?

    @JvmStatic external fun nativeAuthorization(): Int

    @JvmStatic external fun nativeCentralStart()

    @JvmStatic external fun nativeCentralState(): Int

    @JvmStatic external fun nativeScanStart(serviceUuids: Array<String>?)

    @JvmStatic external fun nativeScanStop()

    @JvmStatic external fun nativePeripheralForIdentifier(identifier: String): Long

    @JvmStatic external fun nativePeripheralState(peripheral: Long): Int

    @JvmStatic external fun nativeConnect(peripheral: Long): Boolean

    @JvmStatic external fun nativeCancelConnect(peripheral: Long)

    @JvmStatic external fun nativeReleasePeripheral(peripheral: Long)

    @JvmStatic external fun nativeDiscoverServices(peripheral: Long): Boolean

    @JvmStatic external fun nativeDiscoverCharacteristics(
        peripheral: Long,
        service: Long,
    ): Boolean

    @JvmStatic external fun nativeDiscoverDescriptors(
        peripheral: Long,
        characteristic: Long,
    ): Boolean

    @JvmStatic external fun nativeReadCharacteristic(
        peripheral: Long,
        characteristic: Long,
    ): Boolean

    @JvmStatic external fun nativeWriteCharacteristic(
        peripheral: Long,
        characteristic: Long,
        value: ByteArray,
        withResponse: Boolean,
    ): Boolean

    @JvmStatic external fun nativeCanSendWriteWithoutResponse(peripheral: Long): Boolean

    @JvmStatic external fun nativeReadDescriptor(
        peripheral: Long,
        descriptor: Long,
    ): Boolean

    @JvmStatic external fun nativeWriteDescriptor(
        peripheral: Long,
        descriptor: Long,
        value: ByteArray,
    ): Boolean

    @JvmStatic external fun nativeSetNotify(
        peripheral: Long,
        characteristic: Long,
        enabled: Boolean,
    ): Boolean

    @JvmStatic external fun nativeReadRssi(peripheral: Long): Boolean

    @JvmStatic external fun nativeMaximumWriteLength(
        peripheral: Long,
        withResponse: Boolean,
    ): Int

    @JvmStatic external fun nativeOpenL2cap(
        peripheral: Long,
        psm: Int,
    ): Boolean

    @JvmStatic external fun nativeL2capWrite(
        channel: Long,
        value: ByteArray,
    ): Int

    @JvmStatic external fun nativeL2capClose(channel: Long)

    @JvmStatic external fun nativeL2capPsm(channel: Long): Int

    @JvmStatic external fun nativePmStart()

    @JvmStatic external fun nativePmState(): Int

    @JvmStatic external fun nativePmAddService(
        serviceUuid: String,
        characteristicUuids: Array<String>,
        properties: IntArray,
        permissions: IntArray,
    ): LongArray?

    @JvmStatic external fun nativePmRemoveService(service: Long)

    @JvmStatic external fun nativePmRespond(
        request: Long,
        result: Int,
        value: ByteArray?,
    )

    @JvmStatic external fun nativePmUpdateValue(
        characteristic: Long,
        value: ByteArray,
        central: String?,
    ): Int

    @JvmStatic external fun nativePmStartAdvertising(
        name: String?,
        serviceUuids: Array<String>,
    )

    @JvmStatic external fun nativePmStopAdvertising()

    @JvmStatic external fun nativePmPublishL2cap(encrypted: Boolean)

    @JvmStatic external fun nativePmUnpublishL2cap(psm: Int)
}
