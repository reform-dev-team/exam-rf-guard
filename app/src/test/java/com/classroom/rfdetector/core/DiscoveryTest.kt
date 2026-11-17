package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    private val source = ScanSource.BLUETOOTH_DISCOVERY
    private fun round(vararg addresses: String) = DiscoveryRound(DiscoveryStatus.COMPLETE,
        addresses.map { DiscoveryPeer(it, "Accessory") }, 1000)
    private fun completed(vararg peers: DiscoveryPeer) = DiscoveryRound(DiscoveryStatus.COMPLETE, peers.toList(), 1000)
    private fun groups(tracker: DiscoveryTracker, kind: ScanSource = source) = discoveryGroups(kind,
        tracker.states.getValue(kind).peers, tracker.baselines[kind], tracker.baselineAddresses[kind].orEmpty())

    @Test fun receivingANameForTheSameAddressDoesNotCreateANewCandidate() {
        for (source in listOf(ScanSource.BLUETOOTH_DISCOVERY, ScanSource.WIFI_P2P)) {
            val tracker = DiscoveryTracker(listOf(source))
            tracker.finish(source, DiscoveryRound(DiscoveryStatus.COMPLETE,
                listOf(DiscoveryPeer("00:11:22:33:44:55", null)), 1000), collecting = true)
            tracker.finish(source, DiscoveryRound(DiscoveryStatus.COMPLETE,
                listOf(DiscoveryPeer("00:11:22:33:44:55", "Accessory")), 2000), collecting = false)
            val group = discoveryGroups(source, tracker.states.getValue(source).peers, tracker.baselines[source], tracker.baselineAddresses[source].orEmpty()).single()
            assertFalse("$source must retain the baseline identity when its name arrives", group.increased)
            assertEquals("Accessory", group.peers.single().name)
        }
    }

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

    @Test fun anExistingAddressCanChangeOrLoseItsNameWithoutIncreasing() {
        for (kind in listOf(ScanSource.BLUETOOTH_DISCOVERY, ScanSource.WIFI_P2P)) {
            val tracker = DiscoveryTracker(listOf(kind))
            tracker.finish(kind, completed(DiscoveryPeer("AA", "Original")), collecting = true)
            for (name in listOf("Updated", null, " ")) {
                tracker.finish(kind, completed(DiscoveryPeer("aa", name)), collecting = false)
                val group = groups(tracker, kind).single()
                assertFalse(group.increased)
                assertEquals(1, group.baselineCount)
                assertEquals(name, group.peers.single().name)
            }
        }
    }

    @Test fun aNewSameNamedAddressIsStillFlaggedWhenAKnownAddressGetsItsName() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, completed(DiscoveryPeer("AA", null)), collecting = true)
        tracker.finish(source, completed(DiscoveryPeer("AA", "Accessory"), DiscoveryPeer("BB", "Accessory")), collecting = false)
        val view = groups(tracker)
        assertEquals(1, view.count { it.increased })
        assertEquals("BB", view.single { it.increased }.peers.single().address)
        assertFalse(view.single { it.peers.any { peer -> peer.address == "AA" } }.increased)
    }

    @Test fun aNewUnnamedAddressIsNotHiddenByTheKnownAddressNameChange() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, completed(DiscoveryPeer("AA", null)), collecting = true)
        tracker.finish(source, completed(DiscoveryPeer("AA", "Accessory"), DiscoveryPeer("BB", null)), collecting = false)
        val group = groups(tracker).single()
        assertEquals(1, group.baselineCount)
        assertEquals(2, group.peers.size)
        assertTrue(group.increased)
    }

    @Test fun namesArrivingDuringCollectionReclassifyEarlierRoundsWithoutLeavingAnUnknownAllowance() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, completed(DiscoveryPeer("AA", null)), collecting = true)
        tracker.finish(source, completed(DiscoveryPeer("AA", "Accessory")), collecting = true)
        assertEquals(mapOf(discoveryGroupKey(source, "Accessory") to 1), tracker.baselines[source])
        tracker.finish(source, completed(DiscoveryPeer("AA", "Accessory"), DiscoveryPeer("BB", null)), collecting = false)
        assertEquals("BB", groups(tracker).single { it.increased }.peers.single().address)
    }

    @Test fun knownCollectionNamesAreNotLostWhenALaterRoundOmitsThem() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, completed(DiscoveryPeer("AA", "Accessory")), collecting = true)
        tracker.finish(source, completed(DiscoveryPeer("AA", null)), collecting = true)
        assertEquals(mapOf(discoveryGroupKey(source, "Accessory") to 1), tracker.baselines[source])
        tracker.finish(source, completed(DiscoveryPeer("AA", null)), collecting = false)
        assertFalse(groups(tracker).single().increased)
    }

    @Test fun historicalRotatingAddressesDoNotIncreaseTheSimultaneousBaselineAllowance() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, round("AA"), collecting = true)
        tracker.finish(source, round("BB"), collecting = true)
        assertEquals(2, tracker.baselineAddresses.getValue(source).size)
        assertEquals(1, tracker.baselines.getValue(source).values.single())
        tracker.finish(source, round("AA", "BB"), collecting = false)
        assertTrue(groups(tracker).single().increased)
        tracker.finish(source, round("CC"), collecting = false)
        assertFalse(groups(tracker).single().increased)
    }

    @Test fun reclassifyingEarlierNamesPreservesThePeakCountOfSimultaneousPeers() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, completed(DiscoveryPeer("AA", null), DiscoveryPeer("BB", null)), collecting = true)
        tracker.finish(source, round("AA"), collecting = true)
        tracker.finish(source, round("BB"), collecting = true)
        assertEquals(mapOf(discoveryGroupKey(source, "Accessory") to 2), tracker.baselines[source])
        tracker.finish(source, round("AA", "BB", "CC"), collecting = false)
        assertTrue(groups(tracker).single().increased)
    }

    @Test fun addressReferencesAreScopedBySourceEvenWhenTheAddressAndNameMatch() {
        val p2p = ScanSource.WIFI_P2P
        val tracker = DiscoveryTracker(listOf(source, p2p))
        tracker.finish(source, completed(DiscoveryPeer("aa", null)), collecting = true)
        tracker.finish(p2p, completed(), collecting = true)
        tracker.finish(source, round("AA"), collecting = false)
        tracker.finish(p2p, round("AA"), collecting = false)
        assertFalse(groups(tracker).single().increased)
        assertTrue(groups(tracker, p2p).single().increased)
    }

    @Test fun failedRoundsAndExamObservationsCannotTeachTheBaselineNewNamesOrAddresses() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, completed(DiscoveryPeer("AA", null)), collecting = true)
        val counts = tracker.baselines
        val addresses = tracker.baselineAddresses
        tracker.finish(source, round("AA", "BB").copy(status = DiscoveryStatus.TIMED_OUT), collecting = true)
        tracker.finish(source, round("AA", "BB"), collecting = false)
        assertEquals(counts, tracker.baselines)
        assertEquals(addresses, tracker.baselineAddresses)
        assertEquals("BB", groups(tracker).single { it.increased }.peers.single().address)
        tracker.reset()
        assertTrue(tracker.baselineAddresses.isEmpty())
        tracker.finish(source, completed(), collecting = true)
        tracker.finish(source, round("AA"), collecting = false)
        assertTrue(groups(tracker).single().increased)
    }

    @Test fun laterCollectionMetadataDoesNotMutateAnAlreadyCapturedSnapshot() {
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, completed(DiscoveryPeer("AA", null)), collecting = true)
        val snapshot = SignalSnapshot("Room", 1000, false, emptyList(), tracker.baselines, tracker.baselineAddresses)
        tracker.finish(source, round("AA"), collecting = true)
        val originalGroup = discoveryGroupKey(source, null)
        assertEquals(mapOf(originalGroup to 1), snapshot.discoveryBaselines.getValue(source))
        assertEquals(originalGroup, snapshot.discoveryBaselineAddresses.getValue(source)["AA"])
        assertEquals(discoveryGroupKey(source, "Accessory"), tracker.baselineAddresses.getValue(source)["AA"])
    }
}
