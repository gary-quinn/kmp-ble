package com.atruedev.kmpble.sample.jvm

import com.atruedev.kmpble.BleData
import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.bonding.PairingEvent
import com.atruedev.kmpble.bonding.PairingHandler
import com.atruedev.kmpble.bonding.PairingResponse
import com.atruedev.kmpble.connection.BondingPreference
import com.atruedev.kmpble.connection.ConnectionOptions
import com.atruedev.kmpble.connection.OperationTimeouts
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.error.GattStatus
import com.atruedev.kmpble.logging.BleLogConfig
import com.atruedev.kmpble.logging.PrintBleLogger
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.permissions.checkBlePermissions
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.Scanner
import com.atruedev.kmpble.server.AdvertiseConfig
import com.atruedev.kmpble.server.Advertiser
import com.atruedev.kmpble.server.AdvertiserException
import com.atruedev.kmpble.server.GattServer
import com.atruedev.kmpble.server.ServerException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

private const val USAGE = """kmp-ble desktop sample (Linux BlueZ or macOS arm64)

  permissions                      print checkBlePermissions()
  adapter                          print adapter state and capabilities
  scan [seconds]                   list advertisements (default 10 s)
  connect <identifier> [seconds]   connect, dump the GATT table, read readable
                                   characteristics, print notifications (default 15 s);
                                   connects by identifier when the device is not advertising
  bond <identifier> [seconds]      same as connect with BondingPreference.Required and a
                                   PairingHandler that answers prompts from stdin
  unbond <identifier>              remove the bond (BlueZ RemoveDevice)
  server [seconds] [name]          open a GATT server and advertise it (default 60 s, name
                                   kmp-ble-sample); logs reads and writes, notifies a counter
                                   every second

Add -Dkmpble.log=true for kmp-ble log output (./gradlew :sample-jvm:run -Dkmpble.log=true --args="...")."""

private val SAMPLE_SERVICE = Uuid.parse("6b6d7062-6c65-4e00-8000-000000000001")
private val ECHO_CHARACTERISTIC = Uuid.parse("6b6d7062-6c65-4e00-8000-000000000002")
private val COUNTER_CHARACTERISTIC = Uuid.parse("6b6d7062-6c65-4e00-8000-000000000003")

fun main(args: Array<String>) {
    if (System.getProperty("kmpble.log") == "true") BleLogConfig.logger = PrintBleLogger()
    runBlocking {
        when (args.firstOrNull()) {
            "permissions" -> println(checkBlePermissions())
            "adapter" -> adapter()
            "scan" -> scan(seconds = args.getOrNull(1)?.toIntOrNull() ?: 10)
            "connect", "bond" -> {
                val identifier = args.getOrNull(1) ?: return@runBlocking println(USAGE)
                val options =
                    if (args[0] == "bond") {
                        ConnectionOptions(
                            timeouts = OperationTimeouts(),
                            bondingPreference = BondingPreference.Required,
                            pairingHandler = consolePairingHandler,
                        )
                    } else {
                        ConnectionOptions(timeouts = OperationTimeouts())
                    }
                connect(identifier, seconds = args.getOrNull(2)?.toIntOrNull() ?: 15, options)
            }
            "unbond" -> {
                val identifier = args.getOrNull(1) ?: return@runBlocking println(USAGE)
                unbond(identifier)
            }
            "server" ->
                server(
                    seconds = args.getOrNull(1)?.toIntOrNull() ?: 60,
                    name = args.getOrNull(2) ?: "kmp-ble-sample",
                )
            else -> println(USAGE)
        }
    }
}

private suspend fun adapter() {
    val adapter = BluetoothAdapter()
    val state = withTimeoutOrNull(5.seconds) { adapter.state.first { it != BluetoothAdapterState.Unavailable } }
    println("state=${state ?: adapter.state.value}")
    println("capabilities=${adapter.capabilities}")
    println("bonded=${adapter.getBondedDevices().map { it.value }}")
    adapter.close()
}

private suspend fun scan(seconds: Int) {
    Scanner {
        timeout = seconds.seconds
        emission = EmissionPolicy.FirstThenChanges()
    }.use { scanner ->
        scanner.scanEvents.collect { event ->
            when (event) {
                is ScanEvent.Found -> println(event.advertisement.describe())
                is ScanEvent.Failed -> println("scan failed: ${event.error.errorCode} ${event.error.message}")
            }
        }
    }
}

private suspend fun connect(
    identifier: String,
    seconds: Int,
    options: ConnectionOptions,
) {
    val peripheral = locate(identifier).toPeripheral()
    try {
        try {
            peripheral.connect(options)
        } catch (e: BleException) {
            return println("connect failed: ${e.error}")
        }
        println(
            "connected: mtu=${peripheral.mtu.value} maxWrite=${peripheral.maximumWriteValueLength.value} " +
                "bond=${peripheral.bondState.value} encryption=${peripheral.encryptionLevel.value}",
        )
        dumpAndRead(peripheral)
        observeFor(peripheral, seconds)
        peripheral.disconnect()
        println("disconnected: ${peripheral.state.value}")
    } finally {
        peripheral.close()
    }
}

private fun unbond(identifier: String) {
    val peripheral = knownPeripheral(identifier).toPeripheral()
    try {
        println("bond=${peripheral.bondState.value}")
        println("removeBond: ${peripheral.removeBond()}")
    } finally {
        peripheral.close()
    }
}

private suspend fun locate(identifier: String): Advertisement =
    find(identifier)?.also { println("found ${it.describe()}") }
        ?: knownPeripheral(identifier).also { println("$identifier not advertising; connecting by identifier") }

private suspend fun find(identifier: String): Advertisement? =
    Scanner { emission = EmissionPolicy.All }.use { scanner ->
        withTimeoutOrNull(15.seconds) {
            scanner.scanEvents
                .filterIsInstance<ScanEvent.Found>()
                .map { it.advertisement }
                .firstOrNull { it.identifier.value.equals(identifier, ignoreCase = true) }
        }
    }

private suspend fun dumpAndRead(peripheral: Peripheral) {
    for (service in peripheral.services.value.orEmpty()) {
        println("service ${service.uuid}")
        for (characteristic in service.characteristics) {
            val value =
                if (characteristic.properties.read) {
                    runCatching { peripheral.read(characteristic).toHex() }.getOrElse { "read failed: ${it.message}" }
                } else {
                    ""
                }
            println("  char ${characteristic.uuid} [${characteristic.properties.displayName}] $value")
            characteristic.descriptors.forEach { println("    desc ${it.uuid}") }
        }
    }
}

private suspend fun observeFor(
    peripheral: Peripheral,
    seconds: Int,
) {
    val notifying =
        peripheral.services.value
            .orEmpty()
            .flatMap { it.characteristics }
            .filter { it.properties.notify || it.properties.indicate }
    if (notifying.isEmpty()) return
    println("observing ${notifying.size} characteristic(s) for $seconds s")
    runCatching {
        withTimeout(seconds.seconds) {
            notifying.forEach { characteristic ->
                launch {
                    peripheral
                        .observeValues(
                            characteristic,
                        ).collect { println("  notify ${characteristic.uuid}: ${it.toHex()}") }
                }
            }
        }
    }
}

private val consolePairingHandler =
    PairingHandler { event ->
        println("pairing: $event")
        when (event) {
            is PairingEvent.NumericComparison ->
                PairingResponse.Confirm(
                    ask("confirm %06d [y/N]".format(event.numericValue)).equals("y", ignoreCase = true),
                )
            is PairingEvent.PasskeyRequest ->
                ask("passkey")?.toIntOrNull()?.let { PairingResponse.ProvidePin(it) } ?: PairingResponse.Confirm(false)
            is PairingEvent.JustWorksConfirmation, is PairingEvent.PasskeyNotification -> PairingResponse.Confirm(true)
            is PairingEvent.OutOfBandDataRequest -> PairingResponse.Confirm(false)
        }
    }

private suspend fun ask(prompt: String): String? =
    withContext(Dispatchers.IO) {
        print("$prompt: ")
        System.out.flush()
        readlnOrNull()?.trim()
    }

private suspend fun server(
    seconds: Int,
    name: String,
) {
    val echo = AtomicReference("kmp-ble".encodeToByteArray())
    val counter = AtomicInteger()
    val server =
        GattServer {
            service(SAMPLE_SERVICE) {
                characteristic(ECHO_CHARACTERISTIC) {
                    properties {
                        read = true
                        write = true
                        writeWithoutResponse = true
                    }
                    permissions {
                        read = true
                        write = true
                    }
                    onRead { device ->
                        println("read echo by ${device.value}")
                        BleData(echo.get())
                    }
                    onWrite { device, data, responseNeeded ->
                        val bytes = data.toByteArray()
                        echo.set(bytes)
                        println("write echo by ${device.value}: ${bytes.toHex()} \"${bytes.decodeToString()}\"")
                        if (responseNeeded) GattStatus.Success else null
                    }
                }
                characteristic(COUNTER_CHARACTERISTIC) {
                    properties {
                        read = true
                        notify = true
                    }
                    permissions { read = true }
                    onRead { device ->
                        println("read counter by ${device.value}")
                        BleData(counter.get().toBytes())
                    }
                }
            }
        }
    val advertiser = Advertiser()
    try {
        server.open()
        advertiser.startAdvertising(AdvertiseConfig(name = name, serviceUuids = listOf(SAMPLE_SERVICE)))
        println("advertising \"$name\" service $SAMPLE_SERVICE for $seconds s")
        withTimeoutOrNull(seconds.seconds) {
            launch { server.connectionEvents.collect { println("server: $it") } }
            while (true) {
                delay(1.seconds)
                val value = counter.incrementAndGet()
                if (server.connections.value.isEmpty()) continue
                runCatching { server.notify(COUNTER_CHARACTERISTIC, null, BleData(value.toBytes())) }
                    .onFailure { println("notify failed: ${it.message}") }
            }
        }
    } catch (e: ServerException) {
        println("server failed: ${e.message}")
    } catch (e: AdvertiserException) {
        println("advertising failed: ${e.message}")
    } finally {
        advertiser.close()
        server.close()
        println("server closed")
    }
}

private fun knownPeripheral(identifier: String): Advertisement =
    Advertisement(
        identifier = Identifier(identifier),
        name = null,
        rssi = 0,
        txPower = null,
        isConnectable = true,
        serviceUuids = emptyList(),
        manufacturerData = emptyMap(),
        serviceData = emptyMap(),
        timestampNanos = 0,
    )

private fun Advertisement.describe(): String =
    "${identifier.value} rssi=$rssi name=${name ?: "-"} connectable=$isConnectable " +
        "services=$serviceUuids manufacturer=${manufacturerData.keys.map { "0x%04X".format(it) }}"

private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

private fun Int.toBytes(): ByteArray =
    byteArrayOf(toByte(), (this shr 8).toByte(), (this shr 16).toByte(), (this shr 24).toByte())
