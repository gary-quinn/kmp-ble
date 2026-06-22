package com.atruedev.kmpble.testing

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.periodic.PastException
import com.atruedev.kmpble.periodic.PeriodicAdvertisingSync
import com.atruedev.kmpble.periodic.PeriodicReport
import com.atruedev.kmpble.peripheral.Peripheral
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Fake [PeriodicAdvertisingSync] for unit tests.
 *
 * Provides a controllable report stream via [emitReport] and records
 * [transferTo] calls. Use in tests to simulate periodic advertising
 * sync and PAST transfer behavior without platform dependencies.
 */
public class FakePeriodicAdvertisingSync(
    override val advertiserAddress: Identifier = Identifier("fake-advertiser"),
    override val advertisingSid: Int = 0,
) : PeriodicAdvertisingSync {
    override var isActive: Boolean = true
        private set

    private val _reports = Channel<PeriodicReport>(Channel.BUFFERED)
    override val reports: Flow<PeriodicReport> = _reports.receiveAsFlow()

    /** Record of peripherals that received this sync via [transferTo]. */
    public val transferredTo: MutableList<Peripheral> = mutableListOf()

    override suspend fun transferTo(peripheral: Peripheral) {
        if (!isActive) throw PastException.SyncInactive()
        transferredTo.add(peripheral)
    }

    override fun close() {
        isActive = false
        _reports.close()
    }

    /**
     * Simulate a periodic advertising report from the advertiser.
     */
    public suspend fun emitReport(report: PeriodicReport) {
        _reports.send(report)
    }

    /**
     * Simulate the sync being lost externally.
     */
    public fun simulateLost() {
        isActive = false
        _reports.close(PastException.SyncFailed("Simulated sync loss"))
    }
}
