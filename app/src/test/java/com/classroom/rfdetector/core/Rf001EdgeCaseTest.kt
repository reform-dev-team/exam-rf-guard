package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class Rf001EdgeCaseTest {

    private fun observation(
        address: String,
        timestamp: Long,
        name: String = "SCHOOL"
    ) = RfObservation(
        address,
        ScanSource.WIFI,
        name,
        -55,
        null,
        false,
        timestamp
    )

    @Test
    fun historyOnlySameNameBssidDoesNotAppearInReview() {
        val tracker = DeviceTracker()

        tracker.update(
            observation("10:00:00:00:00:01", 0L)
        )

        val baseline = SignalSnapshot.capture(
            "Classroom",
            1_000L,
            false,
            tracker.snapshot(1_000L, includeInactive = true)
        )

        tracker.update(
            observation("20:11:22:33:44:55", 2_000L)
        )

        // Deliberately advance time beyond the active observation window.
        val now = 1_000_000L

        val devices = tracker.snapshot(
            now,
            includeInactive = true
        )

        assertEquals(
            SignalPresence.HISTORY,
            devices.single { it.key == "20:11:22:33:44:55" }.presence
        )

        val result = watchView(
            devices,
            baselineGroupCounts(baseline),
            emptySet(),
            now,
            baselineWifiAddresses(baseline)
        )

        assertTrue(result.sameNameWifi.isEmpty())
        assertEquals(0, result.count)
    }

    @Test
    fun locallyAdministeredSameNameBssidCurrentBehavior() {
        val tracker = DeviceTracker()

        tracker.update(
            observation("10:00:00:00:00:01", 0L)
        )

        val baseline = SignalSnapshot.capture(
            "Classroom",
            1_000L,
            false,
            tracker.snapshot(1_000L, includeInactive = true)
        )

        // 22 has the locally administered bit set.
        val localAddress = "22:11:22:33:44:55"

        tracker.update(
            observation(localAddress, 100_000L)
        )

        val result = watchView(
            tracker.snapshot(101_000L, includeInactive = true),
            baselineGroupCounts(baseline),
            emptySet(),
            101_000L,
            baselineWifiAddresses(baseline)
        )

        // Characterize existing behavior before deciding on a change.
        assertEquals(0, result.sameNameWifi.size)
        assertEquals(1, result.wifi.size)
        assertEquals(1, result.count)
        assertTrue(result.wifi.single().copiesEmptyRoomName)
    }
}
