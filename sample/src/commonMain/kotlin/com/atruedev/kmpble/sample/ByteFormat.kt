package com.atruedev.kmpble.sample

internal fun humanBytes(bytes: Long): String {
    val absBytes = if (bytes < 0) -bytes else bytes
    return when {
        absBytes >= 1024L * 1024 -> {
            val mib = absBytes.toDouble() / (1024.0 * 1024.0)
            "${roundOne(mib)} MiB"
        }
        absBytes >= 1024L -> {
            val kib = absBytes.toDouble() / 1024.0
            "${roundOne(kib)} KiB"
        }
        else -> "$bytes B"
    }
}

internal fun humanThroughput(kbps: Double): String =
    if (kbps >= 1024.0) "${roundOne(kbps / 1024.0)} MiB/s" else "${roundOne(kbps)} KiB/s"

internal fun roundOne(value: Double): Double = (value * 10.0).toLong() / 10.0
