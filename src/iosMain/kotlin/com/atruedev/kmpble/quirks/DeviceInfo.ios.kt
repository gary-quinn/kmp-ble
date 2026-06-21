package com.atruedev.kmpble.quirks

import platform.UIKit.UIDevice

internal actual fun platformCurrentDevice(): DeviceInfo {
    val device = UIDevice.currentDevice
    // iOS identifies device families by model prefix and idiom
    val model = device.model.lowercase()
    // systemName is always "iOS" on iPhone/iPad, "iPadOS" on iPad (iOS 13+)
    val systemName = device.systemName.lowercase()
    // Use localizedModel for user-friendly display name (e.g. "iPhone", "iPad")
    val display = device.localizedModel.lowercase()
    return DeviceInfo(
        manufacturer = "apple",
        model = model,
        display = display,
    )
}
