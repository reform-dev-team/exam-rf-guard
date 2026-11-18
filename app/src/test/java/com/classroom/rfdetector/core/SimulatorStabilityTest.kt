package com.classroom.rfdetector.core

import com.classroom.rfdetector.sim.RadioSimulator
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class SimulatorStabilityTest {
    @Test fun unchangedRoomHasNoNewSignalsAtFiveAndSixteenMinutes() {
        val simulator = RadioSimulator(Random(7916))
        val tracker = DeviceTracker()
        val start = 1_000_000L
        var baseline: SignalSnapshot? = null
        for (elapsed in 0L..960_000L step 250L) {
            val now = start + elapsed
            simulator.tick(now).forEach(tracker::update)
            if (elapsed == 120_000L) {
                baseline = SignalSnapshot.capture("Room", now, true, tracker.snapshot(now, includeInactive = true))
            }
            if (elapsed == 300_000L || elapsed == 960_000L) {
                val saved = requireNotNull(baseline)
                val view = watchView(tracker.snapshot(now, includeInactive = true), baselineGroupCounts(saved), emptySet(), now, baselineWifiAddresses(saved), baselineSamples(saved))
                assertEquals("Unexpected signals at ${elapsed / 60_000} minutes: $view", 0, view.count)
            }
        }
    }
}
