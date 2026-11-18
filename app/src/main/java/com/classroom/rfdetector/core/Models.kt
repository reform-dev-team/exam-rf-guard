package com.classroom.rfdetector.core

import java.util.Locale

/** 관측 출처 */
enum class ScanSource { BLE, WIFI }

fun radioKey(source: ScanSource, address: String): String = "${source.name}:${address.uppercase(Locale.ROOT)}"

enum class SignalPresence {
    RECENT,
    QUIET,
    HISTORY,
}

/** pauses·pausedMs: 앱을 벗어나 수신이 멈췄던 횟수와 총 시간. 그 사이의 신호는 기록되지 않는다. */
data class ScanSession(val id: String, val startedAtMs: Long, val area: String,
    val pauses: Int = 0, val pausedMs: Long = 0)

/** 거리 등급 — RSSI 기반 추정이며 실제 거리 측정이 아니다. */
enum class DistanceClass(val label: String) {
    VERY_CLOSE("바로 옆"),
    CLOSE("가까움"),
    NEAR("중간"),
    FAR("멀다"),
    UNKNOWN("측정 중"),
}

/** 스캐너/시뮬레이터가 올리는 단일 관측값 */
data class RfObservation(
    val key: String,          // BLE MAC 또는 Wi-Fi BSSID
    val source: ScanSource,
    val name: String?,        // 기기명/SSID. null 이면 미지
    val rssi: Int?,           // null 이면 존재만 확인
    val txPower: Int?,        // 보정된 1m 기준 RSSI(dBm). 일반 AD 송신 출력값과 다르다.
    val hidden: Boolean,      // Wi-Fi SSID 숨김 AP
    val timestampMs: Long,
    val manufacturerIds: Set<Int> = emptySet(),
    val hints: Set<SignalHint> = emptySet(),
    val details: Map<String, String> = emptyMap(),
    val elapsedRealtimeNanos: Long? = null, // BLE packet time on the same clock as motion sensors
) {
    val trackingKey: String get() = radioKey(source, key)
}

/** UI 로 내보내는 추적 단위 */
data class TrackedDevice(
    val key: String,
    val displayName: String,
    val source: ScanSource,
    val hidden: Boolean,
    val rssiEma: Double?,
    val distanceClass: DistanceClass,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val manufacturerIds: Set<Int> = emptySet(),
    val identity: DeviceIdentity = DeviceIdentity(),
    val presence: SignalPresence = SignalPresence.RECENT,
    val lastSeenAgeSeconds: Long = 0,
    val observationCount: Int = 0,
    val peakRssi: Int? = null,
    val periods: List<ObservationPeriod> = emptyList(),
    val referenceRssiAt1m: Double? = null,
    val hints: Set<SignalHint> = emptySet(),
    /** 기기가 알린 나머지 정보(항목 → 값). 화면 표시용이며 저장하지 않는다. */
    val details: Map<String, String> = emptyMap(),
    val fineReadings: List<Int> = emptyList(),
    val fineAtMs: Long = 0L,
) {
    val trackingKey: String get() = radioKey(source, key)
}

const val WEAK_SIGNAL_DBM = -85
