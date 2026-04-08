package com.atruedev.kmpble.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class IdleTrackerTest {
    private val timeSource = TestTimeSource()
    private val tracker = IdleTracker<String>(idleTimeout = 5.minutes, timeSource = timeSource)

    // --- trackOrRefresh ---

    @Test
    fun trackOrRefresh_returns_true_for_new_entry() {
        assertTrue(tracker.trackOrRefresh("a", "centralA"))
    }

    @Test
    fun trackOrRefresh_returns_false_for_existing_entry() {
        tracker.trackOrRefresh("a", "centralA")
        assertFalse(tracker.trackOrRefresh("a", "centralA"))
    }

    @Test
    fun trackOrRefresh_increments_size() {
        tracker.trackOrRefresh("a", "centralA")
        tracker.trackOrRefresh("b", "centralB")
        assertEquals(2, tracker.size)
    }

    @Test
    fun trackOrRefresh_refreshes_activity_preventing_eviction() {
        tracker.trackOrRefresh("a", "centralA")

        // Advance 4 minutes, refresh, advance 4 more — total 8 min but only 4 since refresh
        timeSource += 4.minutes
        tracker.trackOrRefresh("a", "centralA")
        timeSource += 4.minutes

        val evicted = tracker.evictIdle()
        assertTrue(evicted.isEmpty())
    }

    // --- get / contains ---

    @Test
    fun get_returns_tracked_value() {
        tracker.trackOrRefresh("a", "centralA")
        assertEquals("centralA", tracker["a"])
    }

    @Test
    fun get_returns_null_for_unknown_key() {
        assertNull(tracker["unknown"])
    }

    @Test
    fun contains_returns_true_for_tracked_key() {
        tracker.trackOrRefresh("a", "centralA")
        assertTrue("a" in tracker)
    }

    @Test
    fun contains_returns_false_for_unknown_key() {
        assertFalse("unknown" in tracker)
    }

    // --- evictIdle ---

    @Test
    fun evictIdle_removes_entries_beyond_timeout() {
        tracker.trackOrRefresh("a", "centralA")
        timeSource += 6.minutes

        val evicted = tracker.evictIdle()
        assertEquals(1, evicted.size)
        assertEquals("a", evicted[0].first)
        assertEquals("centralA", evicted[0].second)
        assertNull(tracker["a"])
        assertEquals(0, tracker.size)
    }

    @Test
    fun evictIdle_keeps_entries_within_timeout() {
        tracker.trackOrRefresh("a", "centralA")
        timeSource += 3.minutes

        val evicted = tracker.evictIdle()
        assertTrue(evicted.isEmpty())
        assertEquals("centralA", tracker["a"])
    }

    @Test
    fun evictIdle_returns_empty_when_no_entries() {
        assertTrue(tracker.evictIdle().isEmpty())
    }

    @Test
    fun evictIdle_handles_mixed_idle_and_active() {
        tracker.trackOrRefresh("old", "centralOld")
        timeSource += 4.minutes
        tracker.trackOrRefresh("new", "centralNew")
        timeSource += 2.minutes

        val evicted = tracker.evictIdle()
        assertEquals(listOf("old" to "centralOld"), evicted)
        assertNull(tracker["old"])
        assertEquals("centralNew", tracker["new"])
    }

    @Test
    fun evictIdle_at_exact_boundary_does_not_evict() {
        tracker.trackOrRefresh("a", "centralA")
        timeSource += 5.minutes

        val evicted = tracker.evictIdle()
        assertTrue(evicted.isEmpty())
    }

    @Test
    fun evictIdle_just_past_boundary_evicts() {
        tracker.trackOrRefresh("a", "centralA")
        timeSource += 5.minutes + 1.seconds

        val evicted = tracker.evictIdle()
        assertEquals(1, evicted.size)
    }

    @Test
    fun successive_evictions_are_independent() {
        tracker.trackOrRefresh("a", "centralA")
        timeSource += 6.minutes
        tracker.evictIdle()

        tracker.trackOrRefresh("b", "centralB")
        timeSource += 6.minutes

        val evicted = tracker.evictIdle()
        assertEquals(listOf("b" to "centralB"), evicted)
    }

    // --- remove ---

    @Test
    fun remove_returns_value_and_decrements_size() {
        tracker.trackOrRefresh("a", "centralA")
        assertEquals("centralA", tracker.remove("a"))
        assertEquals(0, tracker.size)
        assertNull(tracker["a"])
    }

    @Test
    fun remove_returns_null_for_unknown_key() {
        assertNull(tracker.remove("unknown"))
    }

    // --- clear ---

    @Test
    fun clear_removes_all_entries() {
        tracker.trackOrRefresh("a", "centralA")
        tracker.trackOrRefresh("b", "centralB")
        tracker.clear()
        assertEquals(0, tracker.size)
        assertNull(tracker["a"])
        assertNull(tracker["b"])
    }

    // --- re-track after eviction ---

    @Test
    fun re_tracking_after_eviction_treats_as_new() {
        tracker.trackOrRefresh("a", "centralA")
        timeSource += 6.minutes
        tracker.evictIdle()

        assertTrue(tracker.trackOrRefresh("a", "centralA-v2"))
        assertEquals("centralA-v2", tracker["a"])
    }
}
