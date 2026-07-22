package com.atruedev.kmpble.connection

/**
 * Link-layer encryption state for a BLE connection.
 *
 * Encrypted connections protect data-in-transit with AES-CCM encryption.
 * Applications handling sensitive data (medical, financial, authentication)
 * should verify [ESTABLISHED] before transmitting.
 *
 * ## Platform behavior
 *
 * - **Android**: Derived from `BluetoothGattCallback.onEncryptionChange()` when
 *   available (API 26+), falling back to bond state inference.
 * - **iOS**: CoreBluetooth does not expose encryption state directly. Derived
 *   from bond state (`CBPeripheral` authorization). As a result, short-lived
 *   encryption without bonding (e.g. Just Works pairing) may not be detected.
 *
 * State transitions follow the BLE pairing flow:
 * ```
 * NONE  ->  STARTING  ->  ESTABLISHED
 *   ^                        |
 *   +------------------------+  (on disconnect or bond removal)
 * ```
 */
public enum class EncryptionLevel {
    /** Link is not encrypted. Data is transmitted in plaintext over the air. */
    NONE,

    /** Encryption negotiation is in progress (pairing/bonding handshake). */
    STARTING,

    /** Link-layer encryption is active. Data is protected with AES-CCM. */
    ESTABLISHED,
}
