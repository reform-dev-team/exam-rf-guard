package com.classroom.rfdetector.core

/** A saved observation, scoped by radio transport rather than product name. */
data class SnapshotSignal(
    val address: String,
    val source: ScanSource,
    val name: String,
    val rssi: Double?,
    val manufacturer: String,
    val presence: SignalPresence = SignalPresence.RECENT,
    val manufacturerIds: Set<Int> = emptySet(),
    val hints: Set<SignalHint> = emptySet(),
)

data class SignalSnapshot(
    val area: String,
    val capturedAtMs: Long,
    val simulation: Boolean,
    val signals: List<SnapshotSignal>,
    val discoveryBaselines: Map<ScanSource, Map<String, Int>> = emptyMap(),
) {
    companion object {
        fun capture(area: String, capturedAtMs: Long, simulation: Boolean, devices: List<TrackedDevice>): SignalSnapshot {
            val signals = devices.map { device ->
                    SnapshotSignal(device.key, device.source, device.displayName, device.rssiEma,
                        device.identity.summary, device.presence, device.manufacturerIds.toSet(),
                        device.hints.toSet())
            }
            return SignalSnapshot(area.trim(), capturedAtMs, simulation, signals)
        }
    }
}
