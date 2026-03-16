package com.atruedev.kmpble

import android.content.Context

public object KmpBle {
    internal lateinit var appContext: Context
        private set

    public fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal fun requireContext(): Context {
        check(::appContext.isInitialized) {
            "Call KmpBle.init(context) in your Application.onCreate() before using kmp-ble"
        }
        return appContext
    }
}
