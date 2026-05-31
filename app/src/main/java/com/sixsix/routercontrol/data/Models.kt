package com.sixsix.routercontrol.data

/** A device seen on the WLAN, with live stats. */
data class Device(
    val mac: String,
    val name: String,
    val ip: String,
    val band: String,
    val linkSecs: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val isBlocked: Boolean = false
) {
    val totalBytes: Long get() = rxBytes + txBytes
}

/** Per-device accumulated usage for the persistent counter. */
data class UsageTotals(
    val networkBytes: Long,
    val perDevice: Map<String, Long>,   // mac -> bytes
    val trackingSince: Long,
    val days: Int
)

sealed class RouterResult<out T> {
    data class Ok<T>(val value: T) : RouterResult<T>()
    data class Err(val message: String) : RouterResult<Nothing>()
}
