# Module kmp-ble-bluez

Linux backend for kmp-ble on the JVM. Talks to BlueZ over the system D-Bus and registers itself through `ServiceLoader`, so the portable `Scanner { }`, `Advertisement.toPeripheral()`, `BluetoothAdapter()`, `GattServer { }`, and `Advertiser()` factories use it on Linux.

Supports scanning, connections and the GATT client (with negotiated MTU, reliable writes, bonding, and a `PairingHandler` through `org.bluez.Agent1`), the GATT server, and advertising. See `docs/platform-setup-linux.md`.
