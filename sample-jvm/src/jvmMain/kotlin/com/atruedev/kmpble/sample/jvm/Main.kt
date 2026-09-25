package com.atruedev.kmpble.sample.jvm

import com.atruedev.kmpble.Identifier
import com.atruedev.kmpble.adapter.BluetoothAdapter
import com.atruedev.kmpble.adapter.BluetoothAdapterState
import com.atruedev.kmpble.error.BleException
import com.atruedev.kmpble.logging.BleLogConfig
import com.atruedev.kmpble.logging.PrintBleLogger
import com.atruedev.kmpble.peripheral.Peripheral
import com.atruedev.kmpble.peripheral.toPeripheral
import com.atruedev.kmpble.permissions.checkBlePermissions
import com.atruedev.kmpble.scanner.Advertisement
import com.atruedev.kmpble.scanner.EmissionPolicy
import com.atruedev.kmpble.scanner.ScanEvent
import com.atruedev.kmpble.scanner.Scanner
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private const val USAGE = """kmp-ble desktop sample (Linux BlueZ or macOS arm64)

  permissions                      print checkBlePermissions()
  adapter                          print adapter state and capabilities
  scan [seconds]                   list advertisements (default 10 s)
  connect <identifier> [seconds]   connect, dump the GATT table, read readable
                                   characteristics, print notifications (default 15 s);
                                   connects by identifier when the device is not advertising

Add -Dkmpble.log=true for kmp-ble log output (./gradlew :sample-jvm:run -Dkmpble.log=true --args="...")."""

fun main(args: Array<String>) {
    if (System.getProperty("kmpble.log") == "true") BleLogConfig.logger = PrintBleLogger()
    runBlocking {
        when (args.firstOrNull()) {
            "permissions" -> println(checkBlePermissions())
            "adapter" -> adapter()
            "scan" -> scan(seconds = args.getOrNull(1)?.toIntOrNull() ?: 10)
            "connect" -> {
                val identifier = args.getOrNull(1) ?: return@runBlocking println(USAGE)
                connect(identifier, seconds = args.getOrNull(2)?.toIntOrNull() ?: 15)
            }
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
) {
    val advertisement =
        find(identifier)?.also { println("found ${it.describe()}") }
            ?: knownPeripheral(identifier).also { println("$identifier not advertising; connecting by identifier") }
    val peripheral = advertisement.toPeripheral()
    try {
        try {
            peripheral.connect()
        } catch (e: BleException) {
            return println("connect failed: ${e.error}")
        }
        println(
            "connected: mtu=${peripheral.mtu.value} maxWrite=${peripheral.maximumWriteValueLength.value} bond=${peripheral.bondState.value}",
        )
        dumpAndRead(peripheral)
        observeFor(peripheral, seconds)
        peripheral.disconnect()
        println("disconnected: ${peripheral.state.value}")
    } finally {
        peripheral.close()
    }
}

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
