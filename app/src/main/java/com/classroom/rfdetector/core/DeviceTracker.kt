package com.classroom.rfdetector.core

import java.util.concurrent.ConcurrentHashMap

/** Keep each radio/address independent, including its name, strength and history. */
class DeviceTracker {
    private class Track(val key: String, val source: ScanSource) {
        var name: String? = null
        var hidden = false
        val manufacturerIds = LinkedHashSet<Int>()
        val hints = LinkedHashSet<SignalHint>()
        val details = LinkedHashMap<String, String>()
        var fineReadings: List<Int> = emptyList()
        var fineAtMs = 0L
        var ema: Double? = null
        var tx: Double? = null
        var periods: List<ObservationPeriod> = emptyList()
        var firstSeen = 0L
        var lastSeen = 0L
        var observationCount = 0
        var peakRssi: Int? = null
    }

    private val tracks = ConcurrentHashMap<String, Track>()

    @Synchronized
    fun update(obs: RfObservation) {
        val firstObservation = !tracks.containsKey(obs.trackingKey)
        val t = tracks.getOrPut(obs.trackingKey) {
            Track(obs.key, obs.source).also { it.firstSeen = obs.timestampMs }
        }
        val late = !firstObservation && obs.timestampMs < t.lastSeen
        t.firstSeen = minOf(t.firstSeen, obs.timestampMs)
        t.lastSeen = maxOf(t.lastSeen, obs.timestampMs)
        t.observationCount++
        val previousPeriodCount = t.periods.size
        t.periods = addToPeriods(t.periods, obs.timestampMs, obs.source.historyGapMs())
        t.manufacturerIds.addAll(obs.manufacturerIds)
        t.hints.addAll(obs.hints)
        if (!late) t.details.putAll(obs.details)
        if (!late || t.name == null) obs.name?.takeIf { it.isNotBlank() }?.let { t.name = it }
        if (!late) t.hidden = obs.hidden
        if (!late && obs.source == ScanSource.BLE) {
            val r = obs.rssi
            if (r != null && r in -126..-1) {
                val recent = if (obs.timestampMs - t.fineAtMs <= 3000) t.fineReadings else emptyList()
                t.fineReadings = recent.takeLast(11) + r
                t.fineAtMs = obs.timestampMs
            } else { t.fineReadings = emptyList() }
        }
        obs.rssi?.let { r ->
            if (!late) {
                t.ema = RssiFilter.ema(if (t.periods.size > previousPeriodCount) null else t.ema, r)
                t.tx = obs.txPower?.takeIf { it in -100..20 && it != 127 }?.toDouble() ?: t.tx
            }
            t.peakRssi = maxOf(t.peakRssi ?: r, r)
        }
    }

    @Synchronized
    fun snapshot(nowMs: Long, includeInactive: Boolean = false): List<TrackedDevice> {
        return tracks.values.filter { includeInactive || nowMs - it.lastSeen <= 90_000L }.map { t ->
            val rssi = t.ema
            val dist = rssi?.let { RssiFilter.distanceM(it, t.tx ?: RssiFilter.DEFAULT_TX_DBM) }
            val ageMs = (nowMs - t.lastSeen).coerceAtLeast(0)
            val presence = signalPresence(t.source, t.lastSeen, nowMs)
            TrackedDevice(
                key = t.key,
                displayName = t.name ?: defaultName(t),
                source = t.source,
                hidden = t.hidden,
                rssiEma = rssi,
                distanceClass = RssiFilter.classify(dist),
                firstSeenMs = t.firstSeen,
                lastSeenMs = t.lastSeen,
                manufacturerIds = t.manufacturerIds.toSet(),
                presence = presence,
                lastSeenAgeSeconds = ageMs / 1000,
                observationCount = t.observationCount,
                peakRssi = t.peakRssi,
                periods = t.periods.toList(),
                referenceRssiAt1m = t.tx,
                hints = t.hints.toSet(),
                details = t.details.toMap(), fineReadings = t.fineReadings, fineAtMs = t.fineAtMs,
            )
        }.sortedWith(
            compareBy<TrackedDevice> { it.presence.ordinal }
                .thenByDescending { it.rssiEma ?: Double.NEGATIVE_INFINITY }
        )
    }

    private fun defaultName(t: Track): String = when {
        t.hidden -> "이름 없는 AP"
        t.source == ScanSource.WIFI -> "Wi-Fi 기기 " + t.key.takeLast(5)
        else -> "BLE 기기 " + t.key.takeLast(5)
    }

    @Synchronized
    fun clear() {
        tracks.clear()
    }
}

