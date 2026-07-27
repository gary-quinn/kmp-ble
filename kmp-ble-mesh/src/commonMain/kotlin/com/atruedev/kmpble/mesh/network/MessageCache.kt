package com.atruedev.kmpble.mesh.network

/**
 * Message cache to prevent duplicate forwarding of mesh messages.
 *
 * When a relay node forwards a message, it records the (SRC, SEQ) pair
 * in the cache. If the same message is received again (from a different
 * relay path), it is silently dropped rather than forwarded again.
 *
 * The cache is a fixed-size map with LRU eviction: when full, the
 * least-recently-accessed entry is evicted. Each [isDuplicate] check
 * updates the access timestamp so frequently-seen sources stay in cache.
 */
internal class MessageCache(private val capacity: Int = 256) {
    private val entries = mutableMapOf<Long, Long>()

    /**
     * Check if a message has been seen before.
     *
     * @param src Source address (16-bit).
     * @param seq Sequence number (24-bit).
     * @return true if the message is a duplicate and should be dropped.
     */
    fun isDuplicate(src: Int, seq: Int): Boolean {
        val key = cacheKey(src, seq)
        val now = currentTimeMs()
        return if (entries.containsKey(key)) {
            // Update access timestamp -- this entry was recently seen.
            entries[key] = now
            true
        } else {
            entries[key] = now
            if (entries.size > capacity) {
                evictLru()
            }
            false
        }
    }

    /** Clear the cache. */
    fun clear() { entries.clear() }

    /**
     * Evict the entry with the oldest access timestamp.
     * Walks the map to find the minimum timestamp -- O(n) but the
     * cache is bounded (default 256 entries) so this is acceptable.
     */
    private fun evictLru() {
        var oldestKey: Long? = null
        var oldestTime = Long.MAX_VALUE
        for ((key, time) in entries) {
            if (time < oldestTime) {
                oldestTime = time
                oldestKey = key
            }
        }
        if (oldestKey != null) {
            entries.remove(oldestKey)
        }
    }

    private fun cacheKey(src: Int, seq: Int): Long =
        ((src.toLong() and 0xFFFF) shl 24) or (seq.toLong() and 0xFFFFFF)

    private fun currentTimeMs(): Long =
        kotlin.time.TimeSource.Monotonic.markNow().elapsedNow()
            .inWholeMilliseconds
}
