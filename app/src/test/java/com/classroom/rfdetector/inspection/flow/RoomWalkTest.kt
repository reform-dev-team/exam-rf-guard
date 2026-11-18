package com.classroom.rfdetector.inspection.flow

import org.junit.Assert.*
import org.junit.Test

class RoomWalkTest {
    private val second = 1_000_000_000L

    @Test fun stableHoldContrastProducesApproximateZoneAndPreservesExactKeys() {
        val walk = RoomWalk(0)
        for (point in 0..4) {
            val start = point * 12 * second
            walk.mark(start)
            assertEquals(point, walk.pointIndex)
            assertEquals(start + WALK_HOLD_NANOS, walk.holdEndsAtNanos)
            // Deliberately deliver samples in reverse timestamp order.
            for (offset in 7 downTo 1) {
                walk.accelerate(start + offset * second, 9.8f)
                val rssi = when (point) { 0 -> -40; 4 -> -42; else -> -75 }
                walk.observe("AA:01", start + offset * second, rssi)
                walk.observe("aa:01", start + offset * second, -60)
            }
            walk.advance(start + WALK_HOLD_NANOS)
            if (point < 4) {
                for (offset in 0..8) {
                    val time = start + 8 * second + offset * second / 2
                    walk.accelerate(time, if (offset % 2 == 0) 11.8f else 7.8f)
                    walk.observe("AA:01", time, -80)
                }
            }
        }
        val candidates = walk.result!!
        assertEquals(listOf("AA:01", "aa:01"), candidates.map { it.trackingKey })
        assertEquals(WalkZone("앞쪽 왼쪽", .25f, (1f + .85f) / 2f), candidates[0].zone)
        assertNull(candidates[1].zone)
        assertEquals(5, walk.pointIndex)
        assertNull(walk.holdEndsAtNanos)
    }

    @Test fun insufficientHoldsAndRestingSamplesWithholdZones() {
        val walk = RoomWalk(0)
        for (point in 0..4) {
            val start = point * 10 * second
            walk.mark(start)
            walk.observe("one", start + second, if (point == 0) -30 else -80)
            walk.advance(start + WALK_HOLD_NANOS)
        }
        assertEquals(listOf(WalkCandidate("one", null)), walk.result)
    }

    @Test fun duplicatePacketTimestampsDoNotMakeAHoldAdequate() {
        val walk = RoomWalk(0)
        for (point in 0..4) {
            val start = point * 10 * second
            walk.mark(start)
            repeat(5) { walk.observe("device", start + second, if (point == 0) -30 else -80) }
            walk.advance(start + WALK_HOLD_NANOS)
        }
        assertNull(walk.result!!.single().zone)
    }

    @Test fun finalWindowStopsAtScheduledEndBeforeLateTick() {
        val walk = RoomWalk(0)
        for (point in 0..4) {
            val start = point * 10 * second
            walk.mark(start)
            if (point < 4) walk.advance(start + WALK_HOLD_NANOS)
        }
        val end = 48 * second
        walk.observe("inside", end - 1, -50)
        walk.observe("outside", end, -40)
        assertNull(walk.result)
        walk.advance(end + 100 * second)
        assertEquals(listOf(WalkCandidate("inside", null)), walk.result)
        walk.observe("after", end - 1, -40)
        assertEquals(1, walk.result!!.size)
    }

    @Test fun motionEvidenceIsRequiredAndSingleLegDominanceIsUnstable() {
        fun run(withMotion: Boolean): WalkZone? {
            val walk = RoomWalk(0)
            for (point in 0..4) {
                val start = point * 12 * second
                walk.mark(start)
                for (offset in 1..7) {
                    walk.accelerate(start + offset * second, 9.8f)
                    walk.observe("device", start + offset * second, if (point == 0) -50 else -70)
                }
                walk.advance(start + WALK_HOLD_NANOS)
                if (point < 4) {
                    for (offset in 0..8) {
                        val time = start + 8 * second + offset * second / 2
                        if (withMotion) walk.accelerate(time, if (offset % 2 == 0) 11.8f else 7.8f)
                        if (point == 1) walk.observe("device", time, -30)
                    }
                }
            }
            return walk.result!!.single().zone
        }
        assertEquals(WalkZone("앞쪽 왼쪽", 0f, 1f), run(false))
        assertNull(run(true))
    }
}
