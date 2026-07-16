package com.atruedev.kmpble

import android.content.Context
import com.atruedev.kmpble.gatt.internal.ObservationPersistence

public object KmpBle {
    internal lateinit var appContext: Context
        private set

    public fun init(context: Context) {
        appContext = context.applicationContext
        // ObservationPersistence.context is a separate static field (internal to this
        // module) that AndroidPeripheral needs for every close()/save()/restore() call.
        // It has no public setter of its own, so KmpBle.init() - the one documented
        // entry point every consumer already calls - is responsible for wiring it up.
        // Without this, close() throws IllegalStateException unconditionally and the
        // failure leaves the peripheral registry wedged (see AndroidPeripheral.close()).
        ObservationPersistence.context = appContext
    }

    internal fun requireContext(): Context {
        check(::appContext.isInitialized) {
            "Call KmpBle.init(context) in your Application.onCreate() before using kmp-ble"
        }
        return appContext
    }
}
