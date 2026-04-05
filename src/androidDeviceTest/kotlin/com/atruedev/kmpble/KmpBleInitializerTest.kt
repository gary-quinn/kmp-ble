package com.atruedev.kmpble

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Validates [KmpBleInitializer] and [KmpBle] on a real Android runtime
 * with a real [android.content.Context].
 */
@RunWith(AndroidJUnit4::class)
class KmpBleInitializerTest {
    @Test
    fun init_storesApplicationContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        KmpBle.init(appContext)

        val stored = KmpBle.requireContext()
        assertNotNull(stored)
    }

    @Test
    fun requireContext_returnsApplicationContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        KmpBle.init(appContext)

        val context = KmpBle.requireContext()
        // applicationContext should return itself
        assertTrue(context === context.applicationContext)
    }

    @Test
    fun dependencies_isEmpty() {
        val initializer = KmpBleInitializer()
        assertTrue(initializer.dependencies().isEmpty())
    }
}
