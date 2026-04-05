package com.atruedev.kmpble

import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class KmpBleInitializerHostTest {
    @Before
    fun setup() {
        val field = KmpBle::class.java.getDeclaredField("appContext")
        field.isAccessible = true
        field.set(KmpBle, null)
    }

    @Test
    fun init_storesApplicationContext() {
        val appContext = RuntimeEnvironment.getApplication()
        KmpBle.init(appContext)

        val stored = KmpBle.requireContext()
        assertNotNull(stored)
    }

    @Test
    fun requireContext_returnsApplicationContext() {
        val appContext = RuntimeEnvironment.getApplication()
        KmpBle.init(appContext)

        val context = KmpBle.requireContext()
        assertTrue(context === context.applicationContext)
    }

    @Test
    fun dependencies_isEmpty() {
        val initializer = KmpBleInitializer()
        assertTrue(initializer.dependencies().isEmpty())
    }
}
