package com.atruedev.kmpble.gatt.cache

/**
 * JVM-specific conformance test runner for [GattCache].
 *
 * Uses [createGattCache] which resolves to [JvmGattCache] on JVM.
 * Run: ./gradlew :jvmTest --tests "*JvmGattCacheConformance*"
 */
public class JvmGattCacheConformanceTest : GattCacheConformanceTest() {
    override fun buildCache(maxSize: Int): GattCache = createGattCache(maxSize)
}
