package com.atruedev.kmpble.mesh.internal

/**
 * Structured logging for BLE Mesh operations.
 *
 * Follows the logging pattern from the core library
 * ([com.atruedev.kmpble.logging.BleLogEvent]).
 */
internal object MeshLogger {
    /** Log levels. */
    enum class Level { DEBUG, INFO, WARN, ERROR }

    /** Interface for log consumers. */
    public fun interface Sink {
        public fun log(level: Level, tag: String, message: String, throwable: Throwable?)
    }

    private var sink: Sink = Sink { _, _, _, _ -> }

    /** Set the log sink. */
    fun setSink(sink: Sink) { this.sink = sink }

    fun d(tag: String, msg: String) = sink.log(Level.DEBUG, tag, msg, null)
    fun i(tag: String, msg: String) = sink.log(Level.INFO, tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = sink.log(Level.WARN, tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = sink.log(Level.ERROR, tag, msg, t)

    const val TAG_NETWORK = "MeshNetwork"
    const val TAG_PROVISION = "MeshProvision"
    const val TAG_CRYPTO = "MeshCrypto"
    const val TAG_PROXY = "MeshProxy"
    const val TAG_MODEL = "MeshModel"
}
