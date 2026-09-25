package com.atruedev.kmpble.macos

import com.atruedev.kmpble.macos.internal.NativeBridge
import com.atruedev.kmpble.macos.internal.NativeCallback
import com.atruedev.kmpble.macos.internal.NativeLibrary
import org.junit.Assume.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Loads the real dylib and exercises JNI entry points that do not create a CoreBluetooth
 * manager, so no TCC prompt or Bluetooth hardware is involved. Runs on macOS arm64 hosts
 * that built the native library; skipped elsewhere.
 */
class MacosNativeSmokeTest {
    @BeforeTest
    fun requireNativeHost() {
        assumeTrue(System.getProperty("kmpble.macos.nativeBuilt") == "true" && Macos.isMacosArm64())
    }

    @Test
    fun bundledLibraryLoadsAndAnswersWithoutStartingCoreBluetooth() {
        assertTrue(Macos.isNativeLibraryBundled())
        NativeLibrary.load()
        assertEquals(2, NativeBridge.nativeInit(NativeCallback { _, _, _, _, _, _ -> }))
        NativeBridge.nativeMissingUsageDescriptionHost()?.let { assertTrue(it.startsWith("/"), it) }
        assertTrue(NativeBridge.nativeAuthorization() in 0..3)
        assertEquals(-1, NativeBridge.nativePeripheralState(123_456))
        assertEquals(-1, NativeBridge.nativeL2capWrite(123_456, byteArrayOf(1)))
        assertFalse(NativeBridge.nativeReadRssi(123_456))
    }
}
