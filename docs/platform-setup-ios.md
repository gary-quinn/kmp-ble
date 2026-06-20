# Platform Setup: iOS

This guide covers the Xcode project configuration required to use kmp-ble on iOS.

## Requirements

- iOS 15.0+
- Xcode 15.0+
- Physical device (BLE is not available in the iOS simulator)

## Info.plist Keys

Add these keys to your app's `Info.plist`. All are required for BLE operations.

### NSBluetoothAlwaysUsageDescription

Required on iOS 13+. Describes why your app needs Bluetooth access.
Shown in the system permission dialog the first time your app uses Bluetooth.

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth to communicate with BLE devices.</string>
```

### NSBluetoothPeripheralUsageDescription

Required on iOS 12 and earlier. Apple recommends including it for backward
compatibility even when targeting iOS 13+.

```xml
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to communicate with BLE devices.</string>
```

### Complete Info.plist snippet

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app uses Bluetooth to communicate with BLE devices.</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app uses Bluetooth to communicate with BLE devices.</string>
```

## Background Modes

If your app needs to scan, connect, or maintain BLE connections while in the
background, add these to the Signing & Capabilities tab in Xcode:

### Uses Bluetooth LE accessories (acts-as-central)

Enable this for scanning and connecting to peripherals in the background.

In Xcode:
1. Select your target > Signing & Capabilities
2. Click "+ Capability" > Background Modes
3. Check "Uses Bluetooth LE accessories"

Equivalent plist entry:

```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
</array>
```

### Acts as a Bluetooth LE accessory (acts-as-peripheral)

Enable this if your app advertises or operates a GATT server in the background.

```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-peripheral</string>
</array>
```

### Both modes

```xml
<key>UIBackgroundModes</key>
<array>
    <string>bluetooth-central</string>
    <string>bluetooth-peripheral</string>
</array>
```

## Entitlements

### com.apple.developer.bluetooth-central

Required for CoreBluetooth central role. Usually added automatically when you
enable the "Uses Bluetooth LE accessories" background mode.
If Xcode does not add it, create an entitlements file with:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.developer.bluetooth-central</key>
    <true/>
</dict>
</plist>
```

## Swift Package Manager

Add kmp-ble via SPM in Xcode:

1. **File > Add Package Dependencies**
2. Enter: `https://github.com/gary-quinn/kmp-ble`
3. Select the version and add `KmpBle` to your target

In your Swift code:

```swift
import KmpBle
```

## Permission Request Flow

CoreBluetooth automatically requests permission when your app first accesses
Bluetooth. No manual permission request call is needed on iOS.

The `BluetoothAdapterState.Unauthorized` state signals that permission was
denied. Direct the user to Settings > Privacy > Bluetooth.

## Known iOS Limitations

- BLE scanning in the background is rate-limited by iOS. Scan results arrive
  less frequently than in the foreground.
- iOS caches service/characteristic discovery. If a peripheral's GATT
  structure changes, toggle Bluetooth off/on on the iOS device to clear
  the cache.
- The iOS simulator does not support BLE. Always test on a physical device.
- Maximum ATT MTU is negotiated automatically by CoreBluetooth. The library
  exposes the negotiated MTU via `Peripheral.mtu` but cannot force a
  specific value.
