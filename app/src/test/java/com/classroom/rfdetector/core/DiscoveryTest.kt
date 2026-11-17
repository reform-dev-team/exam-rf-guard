package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    private val source = ScanSource.BLUETOOTH_DISCOVERY
    private fun round(vararg addresses: String) = DiscoveryRound(DiscoveryStatus.COMPLETE,
        addresses.map { DiscoveryPeer(it, "Accessory") }, 1000)

    @Test fun baselineKeepsMaximumPerRoundWithoutAccumulatingRotatingAddresses() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, round("AA", "aa", "BB"), collecting = true)
        tracker.finish(source, round("CC"), collecting = true)
        assertEquals(mapOf(discoveryGroupKey(source, "Accessory") to 2), tracker.baselines[source])
        assertEquals(1, tracker.states.getValue(source).peers.size)
        tracker.finish(source, round("DD", "EE", "FF"), collecting = false)
        val group = discoveryGroups(source, tracker.states.getValue(source).peers, tracker.baselines[source]).single()
        assertTrue(group.increased)
        assertEquals(2, group.baselineCount)
    }

    @Test fun successfulEmptyRoundIsZeroBaselineAndOldMissingBaselineRemainsUnknown() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, round(), collecting = true)
        assertTrue(tracker.baselines.containsKey(source))
        assertTrue(tracker.baselines.getValue(source).isEmpty())
        assertTrue(discoveryGroups(source, round("AA").peers, tracker.baselines[source]).single().increased)
        assertNull(discoveryGroups(source, round("AA").peers, null).single().baselineCount)
        assertFalse(discoveryGroups(source, round("AA").peers, null).single().increased)
    }

    @Test fun failureAndPauseKeepResultsButNeverPretendTheyAreCurrent() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, round("AA"), collecting = true)
        assertTrue(tracker.states.getValue(source).isCurrent(2000))
        tracker.finish(source, DiscoveryRound(DiscoveryStatus.FAILED, reportedAtMs = 3000), collecting = false)
        assertEquals(1, tracker.states.getValue(source).peers.size)
        assertEquals(1000L, tracker.states.getValue(source).reportedAtMs)
        assertFalse(tracker.states.getValue(source).isCurrent(3000))
        tracker.begin(source)
        assertFalse(tracker.states.getValue(source).isCurrent(3000))
        tracker.finish(source, round("BB"), collecting = false)
        assertFalse(tracker.states.getValue(source).isCurrent(DISCOVERY_PERIOD_MS + 2 * DISCOVERY_WINDOW_MS + 1001))
        tracker.pause()
        assertFalse(tracker.states.getValue(source).isCurrent(2000))
        tracker.reset()
        assertTrue(tracker.baselines.isEmpty())
    }

    @Test fun missingRssiAndNamesAreKeptAndSourcesStaySeparate() {
        val peer = DiscoveryPeer("AA", null)
        val first = discoveryGroups(source, listOf(peer), emptyMap()).single()
        val second = discoveryGroups(ScanSource.WIFI_P2P, listOf(peer), emptyMap()).single()
        assertNotEquals(first.key, second.key)
        assertNull(first.peers.single().rssi)
    }
}
