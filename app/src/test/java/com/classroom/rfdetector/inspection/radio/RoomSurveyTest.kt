package com.classroom.rfdetector.inspection.radio

import com.classroom.rfdetector.core.ScanSource
import org.junit.Assert.*
import org.junit.Test

class RoomSurveyTest {
    private fun ns(ms: Long) = ms * 1_000_000
    private val key = "BLE:00:11:22:33:44:55"
    private fun record(model: RoomSurvey, point: Checkpoint, startMs: Long, values: List<Int>) {
        model.begin(point, ns(startMs))
        values.forEachIndexed { index, value ->
            val time = ns(startMs + (index + 1) * 1_000)
            assertTrue(model.accept(model.trackingKey, time, value, time))
        }
        assertTrue(model.finish(ns(startMs + values.size * 1_000L)))
    }

    @Test fun pointUsesPacketTimeNotBroadcastTimeOrSameNameRepresentative() {
        val model = RoomSurvey(key, ScanSource.BLE)
        model.begin(Checkpoint.FRONT_LEFT, ns(10_000))
        assertTrue(model.accept(key, ns(9_999), -55, ns(11_000)))
        assertEquals(0, model.sampleCount)
        assertFalse(model.accept("BLE:00:11:22:33:44:56", ns(11_000), -30, ns(11_000)))
        assertFalse(model.accept("WIFI:00:11:22:33:44:55", ns(11_000), -30, ns(11_000)))
        assertEquals(0, model.sampleCount)
        assertFalse(model.finish(ns(11_000)))
    }

    @Test fun duplicatesFutureAndExpiredPacketsCannotFillCheckpoint() {
        val model = RoomSurvey(key, ScanSource.BLE)
        model.begin(Checkpoint.FRONT_LEFT, ns(1_000))
        assertFalse(model.accept(key, 0, -60, ns(5_000)))
        assertFalse(model.accept(key, ns(6_000), -60, ns(5_000)))
        assertTrue(model.accept(key, ns(2_000), -60, ns(5_000)))
        assertFalse(model.accept(key, ns(2_000), -60, ns(5_000)))
        assertFalse(model.accept(key, ns(3_000), -60, ns(20_000)))
        assertEquals(1, model.sampleCount)
    }

    @Test fun bleNeedsIndependentSamplesAndTimeSpanAndUsesRobustMedian() {
        val model = RoomSurvey(key, ScanSource.BLE)
        record(model, Checkpoint.FRONT_LEFT, 1_000, listOf(-61, -20, -60))
        assertEquals(-60.0, model.readings.getValue(Checkpoint.FRONT_LEFT).median, 0.0)
        assertEquals(1.0, model.readings.getValue(Checkpoint.FRONT_LEFT).dispersion!!, 0.0)
        model.begin(Checkpoint.FRONT_RIGHT, ns(5_000))
        repeat(3) { model.accept(key, ns(5_001L + it), -60, ns(5_001L + it)) }
        assertFalse(model.captureReady(ns(5_004)))
    }

    @Test fun wifiCanRecordOneGenuinelyNewResultWithUnknownNotZeroDispersion() {
        val model = RoomSurvey("WIFI:AP", ScanSource.WIFI)
        model.begin(Checkpoint.FRONT_LEFT, ns(40_000))
        model.accept(model.trackingKey, ns(39_000), -55, ns(41_000))
        assertFalse(model.captureReady(ns(41_000)))
        model.accept(model.trackingKey, ns(41_500), -65, ns(42_000))
        assertTrue(model.finish(ns(42_000)))
        assertEquals(1, model.readings.getValue(Checkpoint.FRONT_LEFT).count)
        assertNull(model.readings.getValue(Checkpoint.FRONT_LEFT).dispersion)
        assertTrue(model.tentative)
    }

    @Test fun wifiCanWaitForMoreIndependentScansBeforeRecording() {
        val model = RoomSurvey("WIFI:AP", ScanSource.WIFI)
        model.begin(Checkpoint.FRONT_LEFT, ns(1_000))
        listOf(-68, -70, -66).forEachIndexed { index, value ->
            val time = ns(2_000L + 30_000L * index)
            model.accept(model.trackingKey, time, value, time)
        }
        assertTrue(model.finish(ns(62_000)))
        assertFalse(model.tentative)
        assertEquals(2.0, model.readings.getValue(Checkpoint.FRONT_LEFT).dispersion!!, 0.0)
    }

    @Test fun expiredSampleSetMustBeGatheredAgain() {
        val model = RoomSurvey(key, ScanSource.BLE)
        record(model, Checkpoint.FRONT_LEFT, 1_000, listOf(-60,-60,-60))
        model.begin(Checkpoint.FRONT_RIGHT, ns(5_000))
        for (time in listOf(6_000L, 7_000L, 8_000L)) model.accept(key, ns(time), -60, ns(time))
        assertFalse(model.finish(ns(30_000)))
        model.accept(key, ns(30_001), -60, ns(30_001))
        assertEquals(1, model.sampleCount)
    }

    @Test fun allFivePointsRequiredAndRetakeRemovesOldComparison() {
        val model = RoomSurvey(key, ScanSource.BLE)
        Checkpoint.entries.forEachIndexed { index, point -> record(model, point, 1_000L + index * 5_000, List(3) { if (point in listOf(Checkpoint.FRONT_LEFT,Checkpoint.BACK_LEFT)) -45 else -70 }) }
        assertEquals(StrongerArea.LEFT, model.strongerArea())
        model.begin(Checkpoint.FRONT_LEFT, ns(30_000))
        assertEquals(4, model.readings.size)
        assertEquals(StrongerArea.UNKNOWN, model.strongerArea())
        model.reset()
        assertTrue(model.readings.isEmpty())
        assertEquals(Checkpoint.FRONT_LEFT, model.nextPoint)
    }

    @Test fun flatOrNoisySetHasNoConfidentArea() {
        for (values in listOf(listOf(-60,-61,-60), listOf(-40,-60,-80))) {
            val model = RoomSurvey(key, ScanSource.BLE)
            Checkpoint.entries.forEachIndexed { index, point -> record(model, point, 1_000L + index * 5_000, values) }
            assertEquals(StrongerArea.UNKNOWN, model.strongerArea())
        }
    }

    @Test fun singleWifiReadingsRemainTentativeEvenWithAnApparentSide() {
        val model = RoomSurvey("WIFI:AP", ScanSource.WIFI)
        Checkpoint.entries.forEachIndexed { index, point ->
            record(model, point, 1_000L + index * 30_000, listOf(if (point in listOf(Checkpoint.FRONT_LEFT, Checkpoint.BACK_LEFT)) -40 else -75))
        }
        assertEquals(StrongerArea.LEFT, model.strongerArea())
        assertTrue(model.tentative)
        assertTrue(model.readings.values.all { it.dispersion == null })
    }

    @Test fun platformCanBeRelativelyStrongWithoutInventingASeat() {
        val model = RoomSurvey(key, ScanSource.BLE)
        Checkpoint.entries.forEachIndexed { index, point -> record(model, point, 1_000L + index * 5_000, List(3) { if(point == Checkpoint.PLATFORM) -40 else -65 }) }
        assertEquals(StrongerArea.PLATFORM, model.strongerArea())
    }

    @Test fun liveTrendStopsForInvalidLostOrStoppedTargetAndDoesNotFollowAnotherKey() {
        val model = RoomSurvey(key, ScanSource.BLE)
        model.accept(key, ns(1_000), -70, ns(1_000))
        assertTrue(model.setReference(ns(1_000)))
        assertEquals(Approach.SAME, model.approach(ns(1_000)))
        repeat(9) { val t = ns(2_000L+it*1_000); model.accept(key,t,-50,t) }
        assertEquals(Approach.STRONGER, model.approach(ns(10_000)))
        model.accept("BLE:OTHER",ns(11_000),-20,ns(11_000))
        assertEquals(Approach.LOST, model.approach(ns(30_000)))
        model.accept(key,ns(31_000),127,ns(31_000))
        assertEquals(Approach.INVALID, model.approach(ns(31_000)))
        assertFalse(model.setReference(ns(31_000)))
        model.stop()
        assertEquals(Approach.LOST, model.approach(ns(31_000)))
    }
}
