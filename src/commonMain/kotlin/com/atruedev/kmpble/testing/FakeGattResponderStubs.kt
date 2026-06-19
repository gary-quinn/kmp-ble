package com.atruedev.kmpble.testing

import com.atruedev.kmpble.gatt.Descriptor

internal suspend fun FakeGattResponder.readDescriptorImpl(descriptor: Descriptor): ByteArray {
    checkNotClosed()
    checkConnected()
    return byteArrayOf()
}

internal suspend fun FakeGattResponder.writeDescriptorImpl(
    descriptor: Descriptor,
    data: ByteArray,
) {
    checkNotClosed()
    checkConnected()
}

internal suspend fun FakeGattResponder.readRssiImpl(): Int {
    checkNotClosed()
    checkConnected()
    return -50
}

internal suspend fun FakeGattResponder.requestMtuImpl(mtu: Int): Int {
    checkNotClosed()
    checkConnected()
    context.updateMtu(mtu)
    return mtu
}
