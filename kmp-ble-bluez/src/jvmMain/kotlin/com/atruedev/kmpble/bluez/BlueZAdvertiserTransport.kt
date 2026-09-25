package com.atruedev.kmpble.bluez

import com.atruedev.kmpble.backend.AdvertiserTransport
import com.atruedev.kmpble.backend.AdvertisingSetSpec
import com.atruedev.kmpble.connection.Phy
import com.atruedev.kmpble.server.AdvertiseMode
import com.atruedev.kmpble.server.AdvertiseTxPower
import com.atruedev.kmpble.server.AdvertiserException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bluez.LEAdvertisement1
import org.bluez.LEAdvertisingManager1
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.ExperimentalUuidApi

/**
 * BlueZ advertising through `LEAdvertisingManager1.RegisterAdvertisement`. Each set is one
 * exported `LEAdvertisement1` object. Interval, TX power, and secondary PHY use BlueZ
 * experimental properties and are ignored by daemons started without `--experimental`.
 */
internal class BlueZAdvertiserTransport(
    private val openBus: () -> DBusConnection = BlueZ::openSystemBus,
) : AdvertiserTransport {
    private val lock = Any()
    private val prefix = "/com/atruedev/kmpble/advertising/adv${instances.incrementAndGet()}"
    private val active = ConcurrentHashMap<Int, String>()

    @Volatile private var bus: DBusConnection? = null

    @Volatile private var adapterPath: String? = null

    override val maxAdvertisingSets: Int by lazy { readSupportedInstances() }

    override suspend fun start(
        setId: Int,
        spec: AdvertisingSetSpec,
    ) {
        withContext(Dispatchers.IO) {
            val (connection, adapter) = connect()
            val path = "$prefix/set$setId"
            val advertisement = BlueZAdvertisement(path, spec)
            try {
                connection.exportObject(path, advertisement)
                connection
                    .getRemoteObject(BlueZ.SERVICE, adapter, LEAdvertisingManager1::class.java)
                    .RegisterAdvertisement(DBusPath(path), emptyMap())
                active[setId] = path
            } catch (e: Exception) {
                runCatching { connection.unExportObject(path) }
                throw AdvertiserException.StartFailed("BlueZ RegisterAdvertisement failed: ${e.message}", e)
            }
        }
    }

    override suspend fun stop(setId: Int) {
        withContext(Dispatchers.IO) { unregister(setId) }
    }

    override fun close() {
        active.keys.toList().forEach(::unregister)
        synchronized(lock) {
            runCatching { bus?.close() }
            bus = null
        }
    }

    private fun unregister(setId: Int) {
        val path = active.remove(setId) ?: return
        val connection = bus ?: return
        val adapter = adapterPath ?: return
        runCatching {
            connection
                .getRemoteObject(
                    BlueZ.SERVICE,
                    adapter,
                    LEAdvertisingManager1::class.java,
                ).UnregisterAdvertisement(DBusPath(path))
        }
        runCatching { connection.unExportObject(path) }
    }

    private fun connect(): Pair<DBusConnection, String> =
        synchronized(lock) {
            val existing = bus
            val existingAdapter = adapterPath
            if (existing != null && existingAdapter != null) return existing to existingAdapter
            if (!BlueZ.isLinux()) throw AdvertiserException.NotSupported("BlueZ advertising requires Linux")
            val connection =
                try {
                    openBus()
                } catch (e: Exception) {
                    throw AdvertiserException.StartFailed("Could not connect to system D-Bus: ${e.message}", e)
                }
            val adapter =
                runCatching { BlueZ.findAdapterPath(connection) }.getOrNull()
                    ?: run {
                        runCatching { connection.close() }
                        throw AdvertiserException.StartFailed("No BlueZ adapter found")
                    }
            bus = connection
            adapterPath = adapter
            connection to adapter
        }

    private fun readSupportedInstances(): Int =
        runCatching {
            val (connection, adapter) = connect()
            val manager = BlueZ.managedObjects(connection)[adapter]?.get(BlueZ.ADVERTISING_MANAGER_INTERFACE)
            (manager?.get("SupportedInstances") as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
        }.getOrDefault(1)

    private companion object {
        val instances = AtomicInteger(0)
    }
}

@OptIn(ExperimentalUuidApi::class)
internal class BlueZAdvertisement(
    private val path: String,
    private val spec: AdvertisingSetSpec,
) : LEAdvertisement1,
    Properties {
    override fun getObjectPath(): String = path

    override fun Release() {}

    fun properties(): Map<String, Variant<*>> =
        buildMap {
            put("Type", Variant(if (spec.connectable) "peripheral" else "broadcast"))
            if (spec.serviceUuids.isNotEmpty()) {
                put(
                    "ServiceUUIDs",
                    Variant(spec.serviceUuids.map { it.toString() }.toTypedArray()),
                )
            }
            if (spec.manufacturerData.isNotEmpty()) {
                put(
                    "ManufacturerData",
                    Variant(
                        spec.manufacturerData.entries.associate { (company, data) -> UInt16(company) to Variant(data) },
                        "a{qv}",
                    ),
                )
            }
            if (spec.serviceData.isNotEmpty()) {
                put(
                    "ServiceData",
                    Variant(
                        spec.serviceData.entries.associate { (uuid, data) ->
                            uuid.toString() to Variant(data)
                        },
                        "a{sv}",
                    ),
                )
            }
            spec.name?.let { put("LocalName", Variant(it)) }
            if (spec.connectable) put("Discoverable", Variant(true))
            if (spec.includeTxPower) put("Includes", Variant(arrayOf("tx-power")))
            val interval = UInt32(intervalMillis(spec.mode))
            put("MinInterval", Variant(interval))
            put("MaxInterval", Variant(interval))
            put("TxPower", Variant(txPowerDbm(spec.txPower)))
            if (!spec.legacy) {
                secondaryChannel(spec.secondaryPhy)?.let { put("SecondaryChannel", Variant(it)) }
            }
        }

    override fun <A : Any?> Get(
        interfaceName: String,
        propertyName: String,
    ): A {
        @Suppress("UNCHECKED_CAST")
        return properties()[propertyName]?.value as A
    }

    override fun <A : Any?> Set(
        interfaceName: String,
        propertyName: String,
        value: A,
    ): Unit = throw org.bluez.Error.NotPermitted("Property $propertyName is read-only")

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()
}

internal fun intervalMillis(mode: AdvertiseMode): Long =
    when (mode) {
        AdvertiseMode.LowLatency -> 100L
        AdvertiseMode.Balanced -> 250L
        AdvertiseMode.LowPower -> 1000L
    }

internal fun txPowerDbm(power: AdvertiseTxPower): Short =
    when (power) {
        AdvertiseTxPower.UltraLow -> -21
        AdvertiseTxPower.Low -> -15
        AdvertiseTxPower.Medium -> -7
        AdvertiseTxPower.High -> 1
    }

internal fun secondaryChannel(phy: Phy?): String? =
    when (phy) {
        Phy.Le1M -> "1M"
        Phy.Le2M -> "2M"
        Phy.LeCoded -> "Coded"
        null -> null
    }
