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
            var bleIndex = 0
            val signals = devices.map { device ->
                    SnapshotSignal(device.key, device.source, device.displayName, device.rssiEma,
                        device.identity.summary, device.presence, device.manufacturerIds.toSet(),
                        device.hints.toSet())
                        .let { if (it.source == ScanSource.BLE) it.withoutAddress(bleIndex++) else it }
            }
            return SignalSnapshot(area.trim(), capturedAtMs, simulation, signals)
        }
    }
}

/**
 * 블루투스 주소는 빈 교실 기록과 비교하는 데 쓰지 않는다. 묶음은 이름·회사 코드·종류 단서로 정하고,
 * 주소로 비교하는 것은 와이파이(같은 공유기 판별)뿐이다. 그래서 개인 기기를 가리킬 수 있는 블루투스 주소는
 * 기록에 남기지 않고 기록 안에서만 구별되는 값으로 바꾼다. 앱이 주소 끝자리로 붙인 이름("BLE 기기 EE:FF")도
 * 주소 일부이므로 지운다. 기기가 스스로 알린 이름은 그대로 두므로 묶음 키와 화면 표시는 바뀌지 않는다.
 */
internal fun SnapshotSignal.withoutAddress(index: Int): SnapshotSignal =
    copy(address = "ble-$index", name = advertisedName(address, name).orEmpty())
