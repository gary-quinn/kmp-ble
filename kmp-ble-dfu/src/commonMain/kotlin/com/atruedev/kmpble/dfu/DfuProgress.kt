package com.atruedev.kmpble.dfu

public sealed interface DfuProgress {

    public data object Starting : DfuProgress

    public data class Transferring(
        val currentObject: Int,
        val totalObjects: Int,
        val bytesSent: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : DfuProgress {
        val fraction: Float get() = if (totalBytes > 0) bytesSent.toFloat() / totalBytes else 0f
    }

    public data class Verifying(val objectIndex: Int) : DfuProgress

    public data object Completing : DfuProgress

    public data object Completed : DfuProgress

    public data class Failed(val error: DfuError) : DfuProgress

    public data object Aborted : DfuProgress
}
