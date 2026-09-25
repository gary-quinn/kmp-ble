# Module kmp-ble-macos

macOS (Apple silicon) backend for kmp-ble on the JVM. Drives CoreBluetooth through a bundled Objective-C shim loaded over JNI and registers itself through `ServiceLoader`, so the portable factories use it on macOS arm64.

Supports scanning, connections and the GATT client, L2CAP channels and listeners, the GATT server, and advertising. The app macOS holds responsible for the process (the packaged `.app`, or the terminal or IDE that launched `java`) must declare `NSBluetoothAlwaysUsageDescription`. See `docs/platform-setup-macos.md`.
