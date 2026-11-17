package com.classroom.rfdetector.inspection.radio

import com.classroom.rfdetector.core.ScanSource
import kotlin.math.abs

/** 교탁을 위로 놓은 순서. 주소를 바꾸거나 같은 이름으로 이어 붙이지 않는다. */
enum class Checkpoint(val label: String) {
    FRONT_LEFT("앞쪽 왼쪽"), FRONT_RIGHT("앞쪽 오른쪽"), BACK_RIGHT("뒤쪽 오른쪽"),
    BACK_LEFT("뒤쪽 왼쪽"), PLATFORM("교탁 앞"),
}
data class PointReading(val median: Double, val dispersion: Double?, val count: Int)
enum class StrongerArea(val label: String) {
    UNKNOWN("차이가 작거나 불안정해 위치를 고르기 어렵습니다"),
    FRONT_LEFT("앞쪽 왼편"), FRONT_RIGHT("앞쪽 오른편"), BACK_LEFT("뒤쪽 왼편"), BACK_RIGHT("뒤쪽 오른편"),
    FRONT("앞쪽"), BACK("뒤쪽"), LEFT("왼편"), RIGHT("오른편"), PLATFORM("교탁 주변"),
}
enum class Approach(val label: String) {
    NO_REFERENCE("출발 세기를 먼저 기록하세요"), LOST("선택한 신호를 새로 받지 못합니다"),
    INVALID("세기를 알 수 없어 비교를 멈췄습니다"), NOISY("변동이 커 비교를 잠시 멈췄습니다"),
    SAME("출발할 때와 비슷함"), STRONGER("출발할 때보다 강해짐"), WEAKER("출발할 때보다 약해짐"),
}

class RoomSurvey(val trackingKey: String, val source: ScanSource) {
    private data class Packet(val time: Long, val rssi: Int)
    private val recent = ArrayDeque<Packet>()
    private val samples = ArrayDeque<Packet>()
    private val recorded = linkedMapOf<Checkpoint, PointReading>()
    val readings: Map<Checkpoint, PointReading> get() = recorded
    val tentative: Boolean get() = recorded.values.any { it.count < 3 }
    var activePoint: Checkpoint? = null
        private set
    private var pointStarted = Long.MAX_VALUE
    private var latestTime = 0L
    private var lastValid = false
    private var reference: Double? = null
    val freshForNanos = (if (source == ScanSource.WIFI) 90_000L else 15_000L) * 1_000_000
    val sampleCount: Int get() = samples.size
    val nextPoint: Checkpoint? get() = Checkpoint.entries.firstOrNull { it !in recorded }

    fun begin(point: Checkpoint, now: Long) {
        activePoint = point
        pointStarted = now
        recorded.remove(point)
        samples.clear()
    }

    /** Android 패킷의 부팅 후 시각만 사용한다. UI/브로드캐스트 수신 시각으로 재표시하지 않는다. */
    fun accept(key: String, packetTime: Long, rssi: Int, now: Long): Boolean {
        if (key != trackingKey || packetTime <= latestTime || packetTime <= 0 || packetTime > now || now - packetTime > freshForNanos) return false
        latestTime = packetTime
        lastValid = rssi in -127..-1
        if (!lastValid) { recent.clear(); return false }
        val packet = Packet(packetTime, rssi)
        recent.addLast(packet)
        while (recent.size > 9 || (recent.isNotEmpty() && now - recent.first().time > freshForNanos)) recent.removeFirst()
        if (activePoint != null && packetTime > pointStarted) {
            samples.addLast(packet)
            while (samples.size > 31 || (samples.isNotEmpty() && now - samples.first().time > freshForNanos)) samples.removeFirst()
        }
        return true
    }

    fun captureReady(now: Long): Boolean = activePoint != null && samples.size >= (if (source == ScanSource.WIFI) 1 else 3) && lastValid &&
        latestTime > 0 && now - latestTime in 0..freshForNanos && now - samples.first().time in 0..freshForNanos &&
        (source == ScanSource.WIFI || samples.last().time - samples.first().time >= 2_000_000_000L)

    fun finish(now: Long): Boolean {
        val point = activePoint ?: return false
        if (!captureReady(now)) return false
        val center = median(samples.map { it.rssi.toDouble() })
        recorded[point] = PointReading(center, if (samples.size >= 3) median(samples.map { abs(it.rssi - center) }) else null, samples.size)
        cancelCapture()
        return true
    }

    fun cancelCapture() { activePoint = null; pointStarted = Long.MAX_VALUE; samples.clear() }
    fun stop() { cancelCapture(); recent.clear(); latestTime = 0; lastValid = false; reference = null }
    fun reset() { stop(); recorded.clear() }

    fun setReference(now: Long): Boolean {
        if (latestTime <= 0 || !lastValid || now - latestTime !in 0..freshForNanos) return false
        val values = recent.filter { now - it.time in 0..freshForNanos }.map { it.rssi.toDouble() }
        if (values.isEmpty()) return false
        reference = median(values)
        return true
    }

    fun approach(now: Long): Approach {
        if (latestTime <= 0 || now - latestTime !in 0..freshForNanos) return Approach.LOST
        if (!lastValid) return Approach.INVALID
        val start = reference ?: return Approach.NO_REFERENCE
        val values = recent.filter { now - it.time in 0..freshForNanos }.map { it.rssi.toDouble() }
        if (values.isEmpty()) return Approach.LOST
        val current = median(values)
        val mad = median(values.map { abs(it - current) })
        if (mad > 5) return Approach.NOISY
        val difference = current - start
        val threshold = maxOf(3.0, 2 * mad)
        return when { difference >= threshold -> Approach.STRONGER; difference <= -threshold -> Approach.WEAKER; else -> Approach.SAME }
    }

    fun strongerArea(): StrongerArea {
        if (recorded.size != 5 || activePoint != null) return StrongerArea.UNKNOWN
        val noise = recorded.values.mapNotNull { it.dispersion }.maxOrNull()
        if (noise != null && noise > 5) return StrongerArea.UNKNOWN
        // 단일 Wi-Fi 결과의 산포는 미상이다. 잠정 비교에는 더 큰 차이를 요구한다.
        val threshold = maxOf(if (tentative) 12.0 else 6.0, (noise ?: 0.0) * 2 + 2)
        fun value(p: Checkpoint) = recorded.getValue(p).median
        val corners = Checkpoint.entries.filter { it != Checkpoint.PLATFORM }
        if (value(Checkpoint.PLATFORM) - corners.maxOf { value(it) } >= threshold) return StrongerArea.PLATFORM
        val frontBack = (value(Checkpoint.FRONT_LEFT) + value(Checkpoint.FRONT_RIGHT) - value(Checkpoint.BACK_LEFT) - value(Checkpoint.BACK_RIGHT)) / 2
        val leftRight = (value(Checkpoint.FRONT_LEFT) + value(Checkpoint.BACK_LEFT) - value(Checkpoint.FRONT_RIGHT) - value(Checkpoint.BACK_RIGHT)) / 2
        val vertical = if (abs(frontBack) >= threshold) if (frontBack > 0) 1 else -1 else 0
        val horizontal = if (abs(leftRight) >= threshold) if (leftRight > 0) 1 else -1 else 0
        return when {
            vertical > 0 && horizontal > 0 -> StrongerArea.FRONT_LEFT
            vertical > 0 && horizontal < 0 -> StrongerArea.FRONT_RIGHT
            vertical < 0 && horizontal > 0 -> StrongerArea.BACK_LEFT
            vertical < 0 && horizontal < 0 -> StrongerArea.BACK_RIGHT
            vertical > 0 -> StrongerArea.FRONT; vertical < 0 -> StrongerArea.BACK
            horizontal > 0 -> StrongerArea.LEFT; horizontal < 0 -> StrongerArea.RIGHT
            else -> StrongerArea.UNKNOWN
        }
    }
}

internal fun median(values: List<Double>): Double {
    require(values.isNotEmpty())
    val sorted = values.sorted()
    return if (sorted.size % 2 == 1) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
}
