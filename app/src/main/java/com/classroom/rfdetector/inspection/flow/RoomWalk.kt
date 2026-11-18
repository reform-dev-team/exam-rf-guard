package com.classroom.rfdetector.inspection.flow

import com.classroom.rfdetector.core.WEAK_SIGNAL_DBM
import kotlin.math.abs
import kotlin.math.floor

internal const val WALK_HOLD_NANOS = 8_000_000_000L
internal const val MAX_WALK_NS = 240_000_000_000L
private const val PACKET_BIN_NS = 100_000_000L
private const val PACKET_BINS = (MAX_WALK_NS / PACKET_BIN_NS).toInt()

/** Screen coordinates, not physical distances. */
internal data class WalkAnchor(val x: Float, val y: Float, val label: String)

/** Approximate normalized position in the guide rectangle, x rightward and y toward the podium. */
internal data class WalkZone(val label: String, val x: Float, val y: Float)
internal data class WalkCandidate(val trackingKey: String, val zone: WalkZone?)

internal val walkAnchors = listOf(
    WalkAnchor(0f, 1f, "앞쪽 왼쪽"), WalkAnchor(0f, 0f, "뒤쪽 왼쪽"),
    WalkAnchor(1f, 0f, "뒤쪽 오른쪽"), WalkAnchor(1f, 1f, "앞쪽 오른쪽"),
    WalkAnchor(.5f, .85f, "교탁 앞"),
)

/**
 * Main-thread-owned collection. All times are elapsed-realtime nanoseconds, including BLE
 * packet and accelerometer timestamps. Windows are half-open. Keep at most the earliest
 * valid packet per address per 100 ms to avoid letting bursts dominate or exhaust storage.
 * Motion is sorted when the walk finishes. Neither input requires callback arrival order.
 */
internal class RoomWalk(private val startedAtNanos: Long) {
    var pointIndex: Int = 0
        private set
    var holdEndsAtNanos: Long? = null
        private set
    var result: List<WalkCandidate>? = null
        private set

    private data class Motion(val time: Long, val magnitude: Float)
    private data class Bin(val x: Float, val y: Float, val rssi: Double, val leg: Int?)
    private data class Leg(val times: List<Long>, val cumulative: List<Double>, val total: Double)

    private val holdStarts = ArrayList<Long>(5)
    private val signals = mutableMapOf<String, LongArray>()
    private val motion = ArrayList<Motion>()

    fun mark(nowNanos: Long) {
        advance(nowNanos)
        if (result != null || pointIndex >= walkAnchors.size || holdEndsAtNanos != null ||
            nowNanos < startedAtNanos || (holdStarts.isNotEmpty() &&
                nowNanos < holdStarts.last() + WALK_HOLD_NANOS)) return
        holdStarts.add(nowNanos)
        holdEndsAtNanos = nowNanos + WALK_HOLD_NANOS
    }

    fun advance(nowNanos: Long) {
        val end = holdEndsAtNanos ?: return
        if (nowNanos < end) return
        holdEndsAtNanos = null
        pointIndex++
        if (pointIndex == walkAnchors.size) result = calculate()
    }

    fun observe(trackingKey: String, timeNanos: Long, rssi: Int) {
        val firstHold = holdStarts.firstOrNull() ?: return
        if (timeNanos < firstHold || !accepts(timeNanos) || rssi !in -126..-1) return
        val slots = signals[trackingKey] ?: run {
            if (signals.size == MAX_KEYS) return
            LongArray(PACKET_BINS).also { signals[trackingKey] = it }
        }
        val elapsed = timeNanos - firstHold
        val index = (elapsed / PACKET_BIN_NS).toInt()
        // Low byte stores -RSSI (never zero); high bits store exact elapsed nanoseconds.
        val packet = (elapsed shl 8) or (-rssi).toLong()
        val old = slots[index]
        if (old == 0L || elapsed < (old ushr 8)) slots[index] = packet
    }

    fun accelerate(timeNanos: Long, magnitude: Float) {
        if (accepts(timeNanos) && magnitude.isFinite() && magnitude >= 0f && motion.size < MAX_MOTION) {
            motion.add(Motion(timeNanos, magnitude))
        }
    }

    private fun accepts(time: Long): Boolean {
        if (result != null || time < startedAtNanos || time - startedAtNanos >= MAX_WALK_NS) return false
        // Stop accepting the final RF window at its scheduled end, even if the tick is late.
        return holdStarts.size < 5 || time < holdStarts[4] + WALK_HOLD_NANOS
    }

    private fun calculate(): List<WalkCandidate> {
        val sortedMotion = motion.sortedBy { it.time }
        val resting = sortedMotion.filter { sample -> holdStarts.any { sample.time >= it && sample.time < it + WALK_HOLD_NANOS } }
        val legs = if (resting.size >= 10) {
            val gravity = median(resting.map { it.magnitude.toDouble() })
            val deviations = resting.map { abs(it.magnitude - gravity) }.sorted()
            val noise = deviations[((deviations.size - 1) * .9).toInt()]
            (0 until 4).map { leg -> makeLeg(sortedMotion, holdStarts[leg] + WALK_HOLD_NANOS, holdStarts[leg + 1], gravity, noise) }
        } else List(4) { null }
        return signals.entries.sortedBy { it.key }.map { (key, samples) ->
            WalkCandidate(key, locate(samples, legs))
        }
    }

    private fun makeLeg(samples: List<Motion>, start: Long, end: Long, gravity: Double, noise: Double): Leg? {
        if (end <= start) return null
        val leg = samples.filter { it.time >= start && it.time < end }
        if (leg.size < 4 || leg.first().time - start > (end - start) / 4 ||
            end - leg.last().time > (end - start) / 4) return null
        val times = ArrayList<Long>(leg.size)
        val cumulative = ArrayList<Double>(leg.size)
        var total = 0.0
        for (i in leg.indices) {
            if (i > 0) {
                val dt = (leg[i].time - leg[i - 1].time) / 1_000_000_000.0
                // A long sensor gap is not evidence of motion across that gap.
                if (dt > 1.0) return null
                val a = (abs(leg[i - 1].magnitude - gravity) - noise).coerceAtLeast(0.0)
                val b = (abs(leg[i].magnitude - gravity) - noise).coerceAtLeast(0.0)
                total += (a + b) * .5 * dt
            }
            times.add(leg[i].time)
            cumulative.add(total)
        }
        if (total < .5) return null
        return Leg(times, cumulative, total)
    }

    private fun locate(samples: LongArray, legs: List<Leg?>): WalkZone? {
        val values = Array(21) { ArrayList<Int>() }
        for (packet in samples) {
            if (packet == 0L) continue
            val time = holdStarts[0] + (packet ushr 8)
            val rssi = -(packet and 0xff).toInt()
            for (i in 0 until 5) {
                if (time >= holdStarts[i] && time < holdStarts[i] + WALK_HOLD_NANOS) {
                    values[i].add(rssi)
                    break
                }
                if (i < 4 && time >= holdStarts[i] + WALK_HOLD_NANOS && time < holdStarts[i + 1]) {
                    val leg = legs[i] ?: break
                    val pos = leg.times.binarySearch(time).let { if (it >= 0) it else -it - 2 }
                    if (pos >= 0 && pos < leg.times.size - 1) {
                        val span = leg.times[pos + 1] - leg.times[pos]
                        val fraction = if (span == 0L) 0.0 else (time - leg.times[pos]).toDouble() / span
                        val activity = leg.cumulative[pos] + fraction * (leg.cumulative[pos + 1] - leg.cumulative[pos])
                        val quartile = floor(activity / leg.total * 4).toInt().coerceIn(0, 3)
                        values[5 + i * 4 + quartile].add(rssi)
                    }
                    break
                }
            }
        }
        val bins = ArrayList<Bin>()
        var holds = 0
        for (i in values.indices) {
            if (values[i].size < 2) continue
            val strength = median(values[i].map { it.toDouble() })
            if (i < 5) {
                holds++
                bins.add(Bin(walkAnchors[i].x, walkAnchors[i].y, strength, null))
            } else {
                val leg = (i - 5) / 4
                val t = ((i - 5) % 4 + .5f) / 4f
                val a = walkAnchors[leg]
                val b = walkAnchors[leg + 1]
                bins.add(Bin(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, strength, leg))
            }
        }
        val holdBins = bins.filter { it.leg == null }
        if (holds < 3) return null
        val strongestHold = holdBins.maxOf { it.rssi }
        if (strongestHold < WEAK_SIGNAL_DBM || strongestHold - holdBins.minOf { it.rssi } < 6.0) return null
        val zone = strongestZone(bins) ?: return null
        for (leg in 0 until 4) {
            if (bins.none { it.leg == leg }) continue
            if (strongestZone(bins.filter { it.leg != leg })?.label != zone.label) return null
        }
        return zone
    }

    private fun strongestZone(bins: List<Bin>): WalkZone? {
        if (bins.isEmpty()) return null
        val max = bins.maxOf { it.rssi }
        val strongest = bins.filter { it.rssi >= max - 3.0 }
        // Disconnected strongest neighborhoods do not justify a location between them.
        if (strongest.maxOf { it.x } - strongest.minOf { it.x } > .67f ||
            strongest.maxOf { it.y } - strongest.minOf { it.y } > .67f) return null
        val x = strongest.map { it.x }.average()
        val y = strongest.map { it.y }.average()
        val column = (x * 3).toInt().coerceIn(0, 2)
        val row = (y * 3).toInt().coerceIn(0, 2)
        val columns = listOf("왼쪽", "가운데", "오른쪽")
        val rows = listOf("뒤쪽", "중간", "앞쪽")
        return WalkZone("${rows[row]} ${columns[column]}", x.toFloat(), y.toFloat())
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }

    private companion object {
        const val MAX_KEYS = 256
        const val MAX_MOTION = 24_000
    }
}
