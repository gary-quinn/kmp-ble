package com.atruedev.kmpble.isochronous

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * Apple platform implementation of [currentTimeMicros] using [NSDate].
 *
 * Uses the system wall clock converted to microseconds since 1970.
 * For relative timing within a session, monotonic clock is preferred
 * but NSDate is the most portable Apple platform API.
 */
internal actual fun currentTimeMicros(): Long = (NSDate().timeIntervalSince1970 * 1_000_000).toLong()
