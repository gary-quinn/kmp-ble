package com.atruedev.kmpble.server

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Tracks entries with monotonic last-activity timestamps and evicts those
 * idle beyond [idleTimeout].
 *
 * Not thread-safe - callers must confine access to a single dispatcher
 * (e.g. `limitedParallelism(1)`).
 */
internal class IdleTracker<T>(
    private val idleTimeout: Duration,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private class Entry<T>(
        var value: T,
        var lastActivity: ComparableTimeMark,
    )

    private val entries = mutableMapOf<String, Entry<T>>()

    val size: Int get() = entries.size

    /**
     * Track a new entry or refresh an existing one's value and timestamp.
     * Returns `true` if the entry was newly added.
     */
    fun trackOrRefresh(
        key: String,
        value: T,
    ): Boolean {
        val existing = entries[key]
        if (existing != null) {
            existing.value = value
            existing.lastActivity = timeSource.markNow()
            return false
        }
        entries[key] = Entry(value, timeSource.markNow())
        return true
    }

    operator fun get(key: String): T? = entries[key]?.value

    operator fun contains(key: String): Boolean = key in entries

    /**
     * Remove and return entries idle longer than [idleTimeout].
     */
    fun evictIdle(): List<Pair<String, T>> {
        val now = timeSource.markNow()
        val evicted = mutableListOf<Pair<String, T>>()
        entries.entries.removeAll { (key, entry) ->
            val idle = (now - entry.lastActivity) > idleTimeout
            if (idle) evicted.add(key to entry.value)
            idle
        }
        return evicted
    }

    fun remove(key: String): T? = entries.remove(key)?.value

    fun clear() = entries.clear()
}
