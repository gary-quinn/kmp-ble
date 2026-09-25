package org.bluez

import org.freedesktop.dbus.exceptions.DBusExecutionException

/**
 * dbus-java names an error reply after the thrown exception's class (`$` becomes `.`), so
 * these nested classes produce the `org.bluez.Error.*` names BlueZ maps to ATT and pairing
 * errors when an exported object rejects a call.
 */
internal class Error private constructor() {
    class Rejected(
        message: String,
    ) : DBusExecutionException(message)

    class Failed(
        message: String,
    ) : DBusExecutionException(message)

    class NotPermitted(
        message: String,
    ) : DBusExecutionException(message)

    class NotAuthorized(
        message: String,
    ) : DBusExecutionException(message)

    class InvalidOffset(
        message: String,
    ) : DBusExecutionException(message)

    class InvalidValueLength(
        message: String,
    ) : DBusExecutionException(message)

    class NotSupported(
        message: String,
    ) : DBusExecutionException(message)
}
