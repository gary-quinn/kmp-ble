@file:Suppress("RemoveRedundantQualifierName")

package com.atruedev.kmpble.connection

@Deprecated(
    message = "State moved to com.atruedev.kmpble.peripheral.state.State",
    replaceWith =
        ReplaceWith(
            "State",
            "com.atruedev.kmpble.peripheral.state.State",
        ),
    level = DeprecationLevel.WARNING,
)
public typealias State = com.atruedev.kmpble.peripheral.state.State
