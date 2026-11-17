package com.classroom.rfdetector.core

import java.util.Locale

data class DistanceEstimate(val meters: Double) {
    val label: String get() = "약 ${formatMeters(meters)} m"
}

fun estimateDistance(rssi: Double?, referenceRssiAt1m: Double? = null, source: ScanSource = ScanSource.BLE): DistanceEstimate? {
    if (rssi == null || !rssi.isFinite() || rssi !in -127.0..-1.0) return null
    val calibrated = referenceRssiAt1m?.takeIf { it.isFinite() && it in -100.0..20.0 }
    val reference = calibrated ?: RssiFilter.defaultReferenceAt1m(source)
    return DistanceEstimate(RssiFilter.distanceM(rssi, reference))
}

private fun formatMeters(value: Double): String = when {
    value < 0.1 -> "<0.1"
    value > 100 -> ">100"
    value >= 10 -> String.format(Locale.getDefault(), "%.0f", value)
    else -> String.format(Locale.getDefault(), "%.1f", value)
}
