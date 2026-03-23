package com.atruedev.kmpble.dfu

/**
 * Observable states of a firmware update.
 *
 * Emitted sequentially by [DfuController.performDfu]:
 * [Starting] → ([Verifying] → [Transferring])* → [Completing] → [Completed].
 * On error the flow emits [Failed]; on cancellation it emits [Aborted].
 */
public sealed interface DfuProgress {

    /** DFU session is being set up (PRN configured, init packet prepared). */
    public data object Starting : DfuProgress

    /**
     * Firmware bytes are being written to the peripheral.
     *
     * @property currentObject zero-based index of the DFU object being transferred
     * @property totalObjects total number of DFU objects in the firmware
     * @property bytesSent bytes transferred so far (init packet + firmware)
     * @property totalBytes total firmware size in bytes
     * @property bytesPerSecond rolling throughput estimate
     * @property fraction progress as 0.0..1.0, suitable for binding to a progress bar
     */
    public data class Transferring(
        val currentObject: Int,
        val totalObjects: Int,
        val bytesSent: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long,
    ) : DfuProgress {
        val fraction: Float get() = if (totalBytes > 0) bytesSent.toFloat() / totalBytes else 0f
    }

    /**
     * CRC32 verification in progress for a completed DFU object.
     *
     * @property objectIndex zero-based index of the object being verified
     */
    public data class Verifying(val objectIndex: Int) : DfuProgress

    /** All objects transferred; executing the firmware on the peripheral. */
    public data object Completing : DfuProgress

    /** Firmware update finished successfully. */
    public data object Completed : DfuProgress

    /** Firmware update failed. Inspect [error] for the cause. */
    public data class Failed(val error: DfuError) : DfuProgress

    /** Firmware update was cancelled via [DfuController.abort] or flow cancellation. */
    public data object Aborted : DfuProgress
}
