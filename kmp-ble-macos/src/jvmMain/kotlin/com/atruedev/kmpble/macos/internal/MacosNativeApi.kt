package com.atruedev.kmpble.macos.internal

/**
 * Seam over [NativeBridge] so every transport is testable without the dylib or Bluetooth.
 * Handles are opaque `Long`s issued by the shim; `false` / negative results mean CoreBluetooth
 * refused the call (unknown handle, not connected, or adapter off).
 */
internal interface MacosNativeApi {
    fun init(callback: NativeCallback)

    /**
     * Path of the app (or executable) whose Info.plist macOS consults for this process and that
     * lacks `NSBluetoothAlwaysUsageDescription`, or null when the key is present or unknowable.
     */
    fun missingUsageDescriptionHost(): String?

    fun authorization(): Int

    fun centralStart()

    fun centralState(): Int

    fun scanStart(serviceUuids: List<String>?)

    fun scanStop()

    fun peripheralForIdentifier(identifier: String): Long

    fun peripheralState(peripheral: Long): Int

    fun connect(peripheral: Long): Boolean

    fun cancelConnect(peripheral: Long)

    fun releasePeripheral(peripheral: Long)

    fun discoverServices(peripheral: Long): Boolean

    fun discoverCharacteristics(
        peripheral: Long,
        service: Long,
    ): Boolean

    fun discoverDescriptors(
        peripheral: Long,
        characteristic: Long,
    ): Boolean

    fun readCharacteristic(
        peripheral: Long,
        characteristic: Long,
    ): Boolean

    fun writeCharacteristic(
        peripheral: Long,
        characteristic: Long,
        value: ByteArray,
        withResponse: Boolean,
    ): Boolean

    fun canSendWriteWithoutResponse(peripheral: Long): Boolean

    fun readDescriptor(
        peripheral: Long,
        descriptor: Long,
    ): Boolean

    fun writeDescriptor(
        peripheral: Long,
        descriptor: Long,
        value: ByteArray,
    ): Boolean

    fun setNotify(
        peripheral: Long,
        characteristic: Long,
        enabled: Boolean,
    ): Boolean

    fun readRssi(peripheral: Long): Boolean

    fun maximumWriteLength(
        peripheral: Long,
        withResponse: Boolean,
    ): Int

    fun openL2cap(
        peripheral: Long,
        psm: Int,
    ): Boolean

    fun l2capWrite(
        channel: Long,
        value: ByteArray,
    ): Int

    fun l2capClose(channel: Long)

    fun pmStart()

    fun pmState(): Int

    fun pmAddService(
        serviceUuid: String,
        characteristicUuids: List<String>,
        properties: IntArray,
        permissions: IntArray,
    ): LongArray?

    fun pmRemoveService(service: Long)

    fun pmRespond(
        request: Long,
        result: Int,
        value: ByteArray?,
    )

    fun pmUpdateValue(
        characteristic: Long,
        value: ByteArray,
        central: String?,
    ): Int

    fun pmStartAdvertising(
        name: String?,
        serviceUuids: List<String>,
    )

    fun pmStopAdvertising()

    fun pmPublishL2cap(encrypted: Boolean)

    fun pmUnpublishL2cap(psm: Int)
}

/** [MacosNativeApi] backed by the bundled dylib. */
internal object JniMacosNativeApi : MacosNativeApi {
    override fun init(callback: NativeCallback) {
        NativeLibrary.load()
        val version = NativeBridge.nativeInit(callback)
        if (version != API_VERSION) {
            throw UnsatisfiedLinkError("libkmpble_macos.dylib API version $version, expected $API_VERSION")
        }
    }

    override fun missingUsageDescriptionHost(): String? = NativeBridge.nativeMissingUsageDescriptionHost()

    override fun authorization(): Int = NativeBridge.nativeAuthorization()

    override fun centralStart() = NativeBridge.nativeCentralStart()

    override fun centralState(): Int = NativeBridge.nativeCentralState()

    override fun scanStart(serviceUuids: List<String>?) {
        NativeBridge.nativeScanStart(serviceUuids?.toTypedArray())
    }

    override fun scanStop() {
        NativeBridge.nativeScanStop()
    }

    override fun peripheralForIdentifier(identifier: String): Long =
        NativeBridge.nativePeripheralForIdentifier(identifier)

    override fun peripheralState(peripheral: Long): Int = NativeBridge.nativePeripheralState(peripheral)

    override fun connect(peripheral: Long): Boolean = NativeBridge.nativeConnect(peripheral)

    override fun cancelConnect(peripheral: Long) {
        NativeBridge.nativeCancelConnect(peripheral)
    }

    override fun releasePeripheral(peripheral: Long) {
        NativeBridge.nativeReleasePeripheral(peripheral)
    }

    override fun discoverServices(peripheral: Long): Boolean = NativeBridge.nativeDiscoverServices(peripheral)

    override fun discoverCharacteristics(
        peripheral: Long,
        service: Long,
    ): Boolean = NativeBridge.nativeDiscoverCharacteristics(peripheral, service)

    override fun discoverDescriptors(
        peripheral: Long,
        characteristic: Long,
    ): Boolean = NativeBridge.nativeDiscoverDescriptors(peripheral, characteristic)

    override fun readCharacteristic(
        peripheral: Long,
        characteristic: Long,
    ): Boolean = NativeBridge.nativeReadCharacteristic(peripheral, characteristic)

    override fun writeCharacteristic(
        peripheral: Long,
        characteristic: Long,
        value: ByteArray,
        withResponse: Boolean,
    ): Boolean = NativeBridge.nativeWriteCharacteristic(peripheral, characteristic, value, withResponse)

    override fun canSendWriteWithoutResponse(peripheral: Long): Boolean =
        NativeBridge.nativeCanSendWriteWithoutResponse(peripheral)

    override fun readDescriptor(
        peripheral: Long,
        descriptor: Long,
    ): Boolean = NativeBridge.nativeReadDescriptor(peripheral, descriptor)

    override fun writeDescriptor(
        peripheral: Long,
        descriptor: Long,
        value: ByteArray,
    ): Boolean = NativeBridge.nativeWriteDescriptor(peripheral, descriptor, value)

    override fun setNotify(
        peripheral: Long,
        characteristic: Long,
        enabled: Boolean,
    ): Boolean = NativeBridge.nativeSetNotify(peripheral, characteristic, enabled)

    override fun readRssi(peripheral: Long): Boolean = NativeBridge.nativeReadRssi(peripheral)

    override fun maximumWriteLength(
        peripheral: Long,
        withResponse: Boolean,
    ): Int = NativeBridge.nativeMaximumWriteLength(peripheral, withResponse)

    override fun openL2cap(
        peripheral: Long,
        psm: Int,
    ): Boolean = NativeBridge.nativeOpenL2cap(peripheral, psm)

    override fun l2capWrite(
        channel: Long,
        value: ByteArray,
    ): Int = NativeBridge.nativeL2capWrite(channel, value)

    override fun l2capClose(channel: Long) {
        NativeBridge.nativeL2capClose(channel)
    }

    override fun pmStart() = NativeBridge.nativePmStart()

    override fun pmState(): Int = NativeBridge.nativePmState()

    override fun pmAddService(
        serviceUuid: String,
        characteristicUuids: List<String>,
        properties: IntArray,
        permissions: IntArray,
    ): LongArray? =
        NativeBridge.nativePmAddService(serviceUuid, characteristicUuids.toTypedArray(), properties, permissions)

    override fun pmRemoveService(service: Long) {
        NativeBridge.nativePmRemoveService(service)
    }

    override fun pmRespond(
        request: Long,
        result: Int,
        value: ByteArray?,
    ) {
        NativeBridge.nativePmRespond(request, result, value)
    }

    override fun pmUpdateValue(
        characteristic: Long,
        value: ByteArray,
        central: String?,
    ): Int = NativeBridge.nativePmUpdateValue(characteristic, value, central)

    override fun pmStartAdvertising(
        name: String?,
        serviceUuids: List<String>,
    ) {
        NativeBridge.nativePmStartAdvertising(name, serviceUuids.toTypedArray())
    }

    override fun pmStopAdvertising() {
        NativeBridge.nativePmStopAdvertising()
    }

    override fun pmPublishL2cap(encrypted: Boolean) {
        NativeBridge.nativePmPublishL2cap(encrypted)
    }

    override fun pmUnpublishL2cap(psm: Int) {
        NativeBridge.nativePmUnpublishL2cap(psm)
    }

    private const val API_VERSION = 2
}
