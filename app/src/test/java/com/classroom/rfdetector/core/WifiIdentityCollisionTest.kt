
package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class WifiIdentityCollisionTest {

    private val baselineAddress = "10:00:00:00:00:01"
    private val newAddress = "20:11:22:33:44:55"

    private fun observe(
        tracker: DeviceTracker,
        address: String,
        name: String = "SCHOOL",
        timestampMs: Long = 0L,
        hidden: Boolean = false
    ) {
        tracker.update(
            RfObservation(
                address,
                ScanSource.WIFI,
                name,
                -55,
                null,
                hidden,
                timestampMs
            )
        )
    }

    private fun classify(
        extraAddresses: List<String>,
        acknowledged: Set<String> = emptySet(),
        extraName: String = "SCHOOL",
        extraHidden: Boolean = false
    ): WatchView {
        val tracker = DeviceTracker()

        observe(tracker, baselineAddress)

        val baseline = SignalSnapshot.capture(
            "Classroom",
            1_000L,
            false,
            tracker.snapshot(1_000L, includeInactive = true)
        )

        extraAddresses.forEachIndexed { index, address ->
            observe(
                tracker,
                address,
                extraName,
                100_000L + index,
                extraHidden
            )
        }

        return watchView(
            tracker.snapshot(101_000L, includeInactive = true),
            baselineGroupCounts(baseline),
            acknowledged,
            101_000L,
            baselineWifiAddresses(baseline)
        )
    }

    @Test
    fun differentBssidWithSameSsidIsReviewOnly() {
        val result = classify(listOf(newAddress))

        assertEquals(0, result.wifi.size)
        assertEquals(0, result.count)
        assertEquals(1, result.sameNameWifi.size)
        assertEquals(newAddress, result.sameNameWifi.single().key)
    }

    @Test
    fun baselineBssidIsNotReviewCandidate() {
        val result = classify(emptyList())

        assertTrue(result.sameNameWifi.isEmpty())
        assertEquals(0, result.count)
    }

    @Test
    fun recognizedRouterAddressVariantIsExcluded() {
        val result = classify(
            listOf("10:00:00:00:00:02")
        )

        assertTrue(result.sameNameWifi.isEmpty())
        assertEquals(0, result.count)
    }

    @Test
    fun hiddenWifiIsNotSameNameReviewCandidate() {
        val result = classify(
            listOf("20:11:22:33:44:55"),
            extraName = "",
            extraHidden = true
        )

        assertTrue(result.sameNameWifi.isEmpty())
    }

    @Test
    fun acknowledgedSameNameGroupIsExcluded() {
        val result = classify(
            listOf(newAddress),
            acknowledged = setOf("WIFI:name:school")
        )

        assertTrue(result.sameNameWifi.isEmpty())
        assertEquals(0, result.count)
    }

    @Test
    fun twoDistinctSameNameBssidsAreBothAvailableForReview() {
        val result = classify(
            listOf(
                "20:11:22:33:44:55",
                "30:11:22:33:44:66"
            )
        )

        assertEquals(2, result.sameNameWifi.size)
        assertEquals(0, result.count)
    }

    @Test
    fun newSsidRemainsAnAlertNotSameNameReview() {
        val result = classify(
            listOf(newAddress),
            extraName = "NEW_NETWORK"
        )

        assertTrue(result.sameNameWifi.isEmpty())
        assertEquals(1, result.wifi.size)
        assertEquals(1, result.count)
    }
}
