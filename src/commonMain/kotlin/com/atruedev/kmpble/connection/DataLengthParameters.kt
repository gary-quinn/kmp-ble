package com.atruedev.kmpble.connection

/**
 * Negotiated LE Data Length Extension parameters for a connection.
 *
 * BLE 4.2 introduced Data Length Extension (DLE), allowing the controller
 * to negotiate larger payload sizes (up to 251 bytes vs the default 27)
 * and longer transmission times for improved throughput.
 *
 * ## Parameter reference (BT Core Spec v4.2, Vol 6, Part B, Section 5.1.9)
 *
 * - [txOctets]: Maximum transmit payload in octets (27..251).
 * - [txTime]: Maximum transmit time in microseconds (328..2120).
 * - [rxOctets]: Maximum receive payload in octets (27..251).
 * - [rxTime]: Maximum receive time in microseconds (328..2120).
 *
 * ## Platform support
 *
 * - **Android**: Controller negotiates DLE automatically when supported
 *   by both devices; no public API to request changes.
 * - **iOS**: CoreBluetooth handles DLE internally; this API
 *   returns the platform-reported values when available, `null` otherwise.
 */
public data class DataLengthParameters(
    /** Maximum transmit payload in octets (27..251). */
    val txOctets: Int,
    /** Maximum transmit time in microseconds (328..2120). */
    val txTime: Int,
    /** Maximum receive payload in octets (27..251). */
    val rxOctets: Int,
    /** Maximum receive time in microseconds (328..2120). */
    val rxTime: Int,
)
