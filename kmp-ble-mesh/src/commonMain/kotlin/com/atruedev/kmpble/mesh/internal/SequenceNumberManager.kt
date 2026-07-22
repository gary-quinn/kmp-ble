package com.atruedev.kmpble.mesh.internal

import com.atruedev.kmpble.mesh.IvIndex
import com.atruedev.kmpble.mesh.MeshAddress
import kotlinx.atomicfu.atomic

/**
 * Manages 24-bit sequence numbers for mesh messages.
 *
 * Each element on a node has its own sequence number that increments
 * for every outbound message. Sequence numbers are critical for replay
 * protection — a (SEQ, IV Index, SRC) tuple must never repeat.
 *
 * On IV Index update, all sequence numbers reset to 0.
 */
internal class SequenceNumberManager(
    initialSeq: Int = 0,
) {
    private val _currentSeq = atomic(initialSeq)

    /** Get the next sequence number and increment. Thread-safe via atomic. */
    fun nextSequenceNumber(): Int = _currentSeq.getAndIncrement()

    /** Current sequence number without incrementing (for state export). */
    fun current(): Int = _currentSeq.value

    /** Check if the sequence number is approaching the 24-bit limit. */
    fun isApproachingLimit(): Boolean = _currentSeq.value > SEQUENCE_WARNING_THRESHOLD

    /** Reset for a new IV Index. */
    fun reset() { _currentSeq.value = 0 }

    companion object {
        /** 24-bit maximum. */
        const val MAX_SEQUENCE: Int = 0xFFFFFF

        /** Warn when at 95% of max. */
        const val SEQUENCE_WARNING_THRESHOLD: Int = (MAX_SEQUENCE * 0.95).toInt()
    }
}

/**
 * Replay Protection List (RPL) for incoming mesh messages.
 *
 * Tracks recently seen (source address, sequence number, IV Index) tuples.
 * Any message with a sequence number <= the last seen value for the same
 * IV Index is rejected as a replay.
 */
internal class ReplayProtectionList(
    private val capacity: Int = 256,
) {
    private val entries = mutableMapOf<Int, ReplayEntry>()

    data class ReplayEntry(
        val lastSequence: Int,
        val ivIndex: IvIndex,
    )

    /**
     * Check if a message should be accepted, and update the RPL.
     *
     * @param src Source unicast address (as Int).
     * @param seq 24-bit sequence number.
     * @param ivIndex Current IV Index.
     * @return true if the message is NOT a replay (should be accepted).
     */
    fun checkAndUpdate(src: Int, seq: Int, ivIndex: IvIndex): Boolean {
        val entry = entries[src]
        return if (entry == null) {
            entries[src] = ReplayEntry(seq, ivIndex)
            evictIfNeeded()
            true
        } else if (entry.ivIndex.value < ivIndex.value) {
            // New IV Index — reset tracking for this source
            entries[src] = ReplayEntry(seq, ivIndex)
            true
        } else if (entry.ivIndex.value == ivIndex.value && seq > entry.lastSequence) {
            entries[src] = ReplayEntry(seq, ivIndex)
            true
        } else {
            false // Replay or old message
        }
    }

    /** Evict oldest entries if over capacity. */
    private fun evictIfNeeded() {
        if (entries.size > capacity) {
            val oldest = entries.entries.first()
            entries.remove(oldest.key)
        }
    }

    /** Clear all entries (e.g., on network reset). */
    fun clear() { entries.clear() }
}
