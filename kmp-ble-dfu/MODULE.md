# Module kmp-ble-dfu

Device Firmware Update (DFU) and Over-The-Air (OTA) update support for kmp-ble.
Start with [DfuController][com.atruedev.kmpble.dfu.DfuController] to perform firmware updates over BLE.
Supports Nordic Secure DFU v2, L2CAP-based OTA, observable progress tracking, and resumable transfers across Android and iOS.

# Package com.atruedev.kmpble.dfu

Entry point for DFU operations. [DfuController][com.atruedev.kmpble.dfu.DfuController] orchestrates the update,
[DfuProgress][com.atruedev.kmpble.dfu.DfuProgress] reports state changes, and
[DfuOptions][com.atruedev.kmpble.dfu.DfuOptions] configures transfer parameters.

# Package com.atruedev.kmpble.dfu.firmware

Firmware package parsing and validation.
[FirmwarePackage][com.atruedev.kmpble.dfu.firmware.FirmwarePackage] handles the Nordic DFU `.zip` format
containing a `manifest.json`, init packet (`.dat`), and firmware binary (`.bin`).

# Package com.atruedev.kmpble.dfu.protocol

DFU protocol implementations. [DfuProtocol][com.atruedev.kmpble.dfu.protocol.DfuProtocol] is the SPI for custom
protocol support; [NordicDfuProtocol][com.atruedev.kmpble.dfu.protocol.NordicDfuProtocol] is the built-in
Nordic Secure DFU v2 implementation.

# Package com.atruedev.kmpble.dfu.transport

BLE transport layer for DFU data transfer. [DfuTransport][com.atruedev.kmpble.dfu.transport.DfuTransport]
abstracts the link between GATT characteristic writes and L2CAP channel writes.

# Package com.atruedev.kmpble.dfu.testing

Test doubles for DFU. Use [FakeDfuTransport][com.atruedev.kmpble.dfu.testing.FakeDfuTransport]
to test DFU protocol flows without BLE hardware.
