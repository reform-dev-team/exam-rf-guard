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
) {
    companion object {
        fun capture(area: String, capturedAtMs: Long, simulation: Boolean, devices: List<TrackedDevice>): SignalSnapshot {
            val replaced = replacedAddresses(devices, capturedAtMs)
            val signals = devices.map { device ->
                    SnapshotSignal(device.key, device.source, device.displayName, device.rssiEma,
                        device.identity.summary, if (device.trackingKey in replaced) SignalPresence.HISTORY else device.presence,
                        device.manufacturerIds.toSet(), device.hints.toSet())
            }
            return SignalSnapshot(area.trim(), capturedAtMs, simulation, signals)
        }
    }
}

/**
 * 저장하는 순간 주소를 막 바꾼 블루투스 기기의 옛 주소. 옛 주소는 마지막 수신 뒤 15초 동안 "잡히는 중"으로 남으므로,
 * 그대로 세면 한 대가 두 대로 기록되고 시험 중 같은 종류가 한 대 늘어도 알리지 못한다.
 * 같은 묶음에 처음 잡힌 지 [ESTABLISHED_MS]가 안 된 주소가 있으면, 그 수만큼 수신이 끊긴 주소(오래 끊긴 것부터)를 지나간 것으로 기록한다.
 * 감시 중에 새 주소를 [ESTABLISHED_MS] 동안 세지 않는 것과 같은 겹침을 기록 쪽에서 거르는 것이다.
 */
private fun replacedAddresses(devices: List<TrackedDevice>, nowMs: Long): Set<String> = devices
    .filter { it.source == ScanSource.BLE && it.presence != SignalPresence.HISTORY }.groupBy { it.groupKey }.values
    .flatMap { members ->
        val fresh = members.count { it.presence == SignalPresence.RECENT && nowMs - it.firstSeenMs < ESTABLISHED_MS }
        members.filter { it.presence == SignalPresence.QUIET && nowMs - it.firstSeenMs >= ESTABLISHED_MS }.sortedBy { it.lastSeenMs }.take(fresh)
    }.map { it.trackingKey }.toSet()
