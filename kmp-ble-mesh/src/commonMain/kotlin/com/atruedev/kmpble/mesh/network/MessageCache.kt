package com.atruedev.kmpble.mesh.network

/**
 * Message cache to prevent duplicate forwarding of mesh messages.
 *
 * When a relay node forwards a message, it records the (SRC, SEQ) pair
 * in the cache. If the same message is received again (from a different
 * relay path), it is silently dropped rather than forwarded again.
 *
 * The cache is a fixed-size circular buffer. Oldest entries are evicted
 * when the cache fills up.
 */
internal class MessageCache(private val capacity: Int = 256) {
    private val entries = LinkedHashMap<Long, Long>(capacity, 0.75f, true)

    /**
     * Check if a message has been seen before.
     *
     * @param src Source address (16-bit).
     * @param seq Sequence number (24-bit).
     * @return true if the message is a duplicate and should be dropped.
     */
    fun isDuplicate(src: Int, seq: Int): Boolean {
        val key = cacheKey(src, seq)
        return if (entries.containsKey(key)) {
            true
        } else {
            entries[key] = currentTimeMs()
            if (entries.size > capacity) {
                entries.remove(entries.keys.first())
            }
            false
        }
    }

    /** Clear the cache. */
    fun clear() { entries.clear() }

    private fun cacheKey(src: Int, seq: Int): Long =
        ((src.toLong() and 0xFFFF) shl 24) or (seq.toLong() and 0xFFFFFF)

    private fun currentTimeMs(): Long =
        kotlin.time.TimeSource.Monotonic.markNow().elapsedNow()
            .inWholeMilliseconds
}
