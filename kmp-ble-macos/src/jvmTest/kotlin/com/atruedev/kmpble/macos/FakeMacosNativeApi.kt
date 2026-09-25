package com.atruedev.kmpble.macos

import com.atruedev.kmpble.macos.internal.AttributeLine
import com.atruedev.kmpble.macos.internal.CbManagerState
import com.atruedev.kmpble.macos.internal.MacosEvent
import com.atruedev.kmpble.macos.internal.MacosNativeApi
import com.atruedev.kmpble.macos.internal.MacosStack
import com.atruedev.kmpble.macos.internal.NativeCallback
import com.atruedev.kmpble.macos.internal.PmWrite
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * CoreBluetooth stand-in: every accepted call answers asynchronously on a single "delegate
 * queue" thread, like the real shim.
 */
internal class FakeMacosNativeApi : MacosNativeApi {
    lateinit var stack: MacosStack
    private val queue = Executors.newSingleThreadExecutor { Thread(it, "fake-cb-queue").apply { isDaemon = true } }

    var initialCentralState = CbManagerState.POWERED_ON
    var initialPmState = CbManagerState.POWERED_ON
    var missingUsageHost: String? = null
    var authorizationValue = 3
    var knownPeripherals = mutableMapOf("7F3C0E7A-0000-4000-8000-000000000001" to PERIPHERAL)
    var connectAnswer: ((Long) -> MacosEvent?) = { MacosEvent.Connected(it) }
    var canSendWithoutResponse = true
    var updateValueResults = ArrayDeque<Int>()
    var l2capWriteResult = 0

    val calls = CopyOnWriteArrayList<String>()

    @Volatile var answerL2capOpen = true

    @Volatile var liveCentralState = CbManagerState.UNKNOWN

    @Volatile var answerReads = true
    val writes = CopyOnWriteArrayList<Triple<Long, ByteArray, Boolean>>()
    val notifyCalls = CopyOnWriteArrayList<Pair<Long, Boolean>>()
    val responses = CopyOnWriteArrayList<Triple<Long, Int, ByteArray?>>()
    val updates = CopyOnWriteArrayList<Triple<Long, ByteArray, String?>>()
    val advertising = CopyOnWriteArrayList<Pair<String?, List<String>>>()
    val l2capWrites = CopyOnWriteArrayList<Pair<Long, ByteArray>>()
    var nextServiceHandle = 500L

    fun emit(event: MacosEvent) {
        queue.execute { stack.dispatch(event) }
    }

    fun emitNow(event: MacosEvent) {
        stack.dispatch(event)
    }

    override fun init(callback: NativeCallback) {
        calls += "init"
    }

    override fun missingUsageDescriptionHost(): String? = missingUsageHost

    override fun authorization(): Int = authorizationValue

    override fun centralStart() {
        calls += "centralStart"
        emit(MacosEvent.CentralState(initialCentralState))
    }

    override fun centralState(): Int = liveCentralState

    override fun scanStart(serviceUuids: List<String>?) {
        calls += "scanStart"
    }

    override fun scanStop() {
        calls += "scanStop"
    }

    override fun peripheralForIdentifier(identifier: String): Long = knownPeripherals[identifier] ?: 0L

    override fun peripheralState(peripheral: Long): Int = 2

    override fun connect(peripheral: Long): Boolean {
        calls += "connect"
        connectAnswer(peripheral)?.let(::emit)
        return true
    }

    override fun cancelConnect(peripheral: Long) {
        calls += "cancelConnect"
        emit(MacosEvent.Disconnected(peripheral, 0, null))
    }

    override fun releasePeripheral(peripheral: Long) {
        calls += "release"
    }

    override fun discoverServices(peripheral: Long): Boolean {
        emit(MacosEvent.ServicesDiscovered(peripheral, 0, null, listOf(AttributeLine(SERVICE, "180D"))))
        return true
    }

    override fun discoverCharacteristics(
        peripheral: Long,
        service: Long,
    ): Boolean {
        emit(
            MacosEvent.CharacteristicsDiscovered(
                peripheral,
                service,
                0,
                null,
                listOf(AttributeLine(HEART_RATE, "2A37", 0x12), AttributeLine(CONTROL, "2A39", 0x0C)),
            ),
        )
        return true
    }

    override fun discoverDescriptors(
        peripheral: Long,
        characteristic: Long,
    ): Boolean {
        val descriptors = if (characteristic == HEART_RATE) listOf(AttributeLine(CCCD, "2902")) else emptyList()
        emit(MacosEvent.DescriptorsDiscovered(peripheral, characteristic, 0, null, descriptors))
        return true
    }

    override fun readCharacteristic(
        peripheral: Long,
        characteristic: Long,
    ): Boolean {
        calls += "read"
        if (answerReads) emit(MacosEvent.ValueUpdated(peripheral, characteristic, 0, null, byteArrayOf(0x4B)))
        return true
    }

    override fun writeCharacteristic(
        peripheral: Long,
        characteristic: Long,
        value: ByteArray,
        withResponse: Boolean,
    ): Boolean {
        writes += Triple(characteristic, value, withResponse)
        if (withResponse) emit(MacosEvent.ValueWritten(peripheral, characteristic, 0, null))
        return true
    }

    override fun canSendWriteWithoutResponse(peripheral: Long): Boolean = canSendWithoutResponse

    override fun readDescriptor(
        peripheral: Long,
        descriptor: Long,
    ): Boolean {
        emit(MacosEvent.DescriptorValue(peripheral, descriptor, 0, null, byteArrayOf(0x01, 0x00)))
        return true
    }

    override fun writeDescriptor(
        peripheral: Long,
        descriptor: Long,
        value: ByteArray,
    ): Boolean {
        emit(MacosEvent.DescriptorWritten(peripheral, descriptor, 0, null))
        return true
    }

    override fun setNotify(
        peripheral: Long,
        characteristic: Long,
        enabled: Boolean,
    ): Boolean {
        notifyCalls += characteristic to enabled
        emit(MacosEvent.NotifyState(peripheral, characteristic, 0, null, enabled))
        return true
    }

    override fun readRssi(peripheral: Long): Boolean {
        emit(MacosEvent.Rssi(peripheral, -61, 0, null))
        return true
    }

    override fun maximumWriteLength(
        peripheral: Long,
        withResponse: Boolean,
    ): Int = 182

    override fun openL2cap(
        peripheral: Long,
        psm: Int,
    ): Boolean {
        calls += "openL2cap"
        if (!answerL2capOpen) return true
        val rejected = psm == REJECTED_PSM
        emit(MacosEvent.L2capOpened(peripheral, if (rejected) 0 else CHANNEL, if (rejected) 1005 else 0, null))
        return true
    }

    override fun l2capWrite(
        channel: Long,
        value: ByteArray,
    ): Int {
        l2capWrites += channel to value
        return l2capWriteResult
    }

    override fun l2capClose(channel: Long) {
        calls += "l2capClose"
    }

    override fun pmStart() {
        calls += "pmStart"
        emit(MacosEvent.PmState(initialPmState))
    }

    override fun pmState(): Int = CbManagerState.UNKNOWN

    override fun pmAddService(
        serviceUuid: String,
        characteristicUuids: List<String>,
        properties: IntArray,
        permissions: IntArray,
    ): LongArray {
        val service = nextServiceHandle++
        val handles = LongArray(characteristicUuids.size + 1)
        handles[0] = service
        for (i in characteristicUuids.indices) handles[i + 1] = nextServiceHandle++
        emit(MacosEvent.PmServiceAdded(service, 0, null))
        return handles
    }

    override fun pmRemoveService(service: Long) {
        calls += "pmRemoveService"
    }

    override fun pmRespond(
        request: Long,
        result: Int,
        value: ByteArray?,
    ) {
        responses += Triple(request, result, value)
    }

    override fun pmUpdateValue(
        characteristic: Long,
        value: ByteArray,
        central: String?,
    ): Int {
        updates += Triple(characteristic, value, central)
        return updateValueResults.removeFirstOrNull() ?: 1
    }

    override fun pmStartAdvertising(
        name: String?,
        serviceUuids: List<String>,
    ) {
        advertising += name to serviceUuids
        emit(MacosEvent.PmAdvertisingStarted(0, null))
    }

    override fun pmStopAdvertising() {
        calls += "pmStopAdvertising"
    }

    override fun pmPublishL2cap(encrypted: Boolean) {
        emit(MacosEvent.PmL2capPublished(0x81, 0, null))
    }

    override fun pmUnpublishL2cap(psm: Int) {
        calls += "pmUnpublish:$psm"
    }

    companion object {
        const val IDENTIFIER = "7F3C0E7A-0000-4000-8000-000000000001"
        const val PERIPHERAL = 10L
        const val SERVICE = 20L
        const val HEART_RATE = 21L
        const val CONTROL = 22L
        const val CCCD = 23L
        const val CHANNEL = 40L
        const val REJECTED_PSM = 0x99

        fun stack(configure: FakeMacosNativeApi.() -> Unit = {}): Pair<MacosStack, FakeMacosNativeApi> {
            val api = FakeMacosNativeApi().apply(configure)
            val stack = MacosStack(api)
            api.stack = stack
            return stack to api
        }

        fun write(
            characteristic: Long,
            offset: Int,
            value: ByteArray,
        ): PmWrite = PmWrite(characteristic, offset, value)
    }
}
