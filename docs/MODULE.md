# Module kmp-ble

Kotlin Multiplatform BLE library for Android, iOS, and JVM.

## Core APIs

| Class | Description |
|-------|-------------|
| [com.atruedev.kmpble.scanner.Scanner] | Discover nearby BLE peripherals |
| [com.atruedev.kmpble.peripheral.Peripheral] | Connect and interact with a BLE device |
| [com.atruedev.kmpble.server.GattServer] | Host a local GATT server |
| [com.atruedev.kmpble.advertiser.Advertiser] | Broadcast BLE advertisements |
| [com.atruedev.kmpble.l2cap.L2capChannel] | Low-latency L2CAP Connection-Oriented Channel |

## Extension Modules

| Module | Description |
|--------|-------------|
| [kmp-ble-codec](kmp-ble-codec/index.html) | Format-agnostic codec layer for typed serialization |
| [kmp-ble-dfu](kmp-ble-dfu/index.html) | Device Firmware Update (DFU) support |
| [kmp-ble-profiles](kmp-ble-profiles/index.html) | Type-safe BLE GATT profile definitions |
| [kmp-ble-quirks](kmp-ble-quirks/index.html) | Curated OEM device quirks database |
