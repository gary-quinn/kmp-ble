package com.atruedev.kmpble

import kotlin.jvm.JvmInline

/**
 * Platform-specific device identifier.
 * - Android: MAC address string (e.g., "AA:BB:CC:DD:EE:FF")
 * - iOS: CBPeripheral.identifier UUID string (NOT a MAC address)
 */
@JvmInline
public value class Identifier(public val value: String)
