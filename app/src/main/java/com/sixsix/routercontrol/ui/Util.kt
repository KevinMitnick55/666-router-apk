package com.sixsix.routercontrol.ui

fun humanBytes(b: Long): String = when {
    b >= 1_000_000_000_000L -> String.format("%.2f TB", b / 1e12)
    b >= 1_000_000_000L -> String.format("%.2f GB", b / 1e9)
    b >= 1_000_000L -> String.format("%.1f MB", b / 1e6)
    b >= 1_000L -> String.format("%.1f KB", b / 1e3)
    else -> "$b B"
}

fun humanDuration(secs: Long): String {
    val h = secs / 3600; val m = (secs % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${secs}s"
    }
}
