package com.classroom.rfdetector.core

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionRecoveryStoreTest {
    @get:Rule val directory = TemporaryFolder()
    private val record = SessionRecovery(
        ScanSession("session", 100, "시험실", 2, 1000, 1, 2000),
        SignalSnapshot("시험실", 90, false, listOf(SnapshotSignal("10:20:30:40:50:60", ScanSource.WIFI, "AP", -60.0, "unknown"))),
        setOf("BLE:name:earbuds"), 2000, 1900,
    )

    @Test fun roundTripIncludesBaselineAndDecisionsButNoLiveObservations() {
        val store = SessionRecoveryStore(directory.root)
        store.save(record)
        assertEquals(record, store.load())
        val json = File(directory.root, "session-recovery.json").readText()
        assertFalse(json.contains("devices"))
        assertFalse(json.contains("watch"))
    }

    @Test fun normalEndTombstoneReplacesCheckpointWithoutChangingBaselineArchive() {
        val baselineStore = SnapshotStore(directory.root)
        baselineStore.save(listOf(record.baseline))
        val store = SessionRecoveryStore(directory.root)
        store.save(record)
        store.save(null)
        assertNull(store.load())
        assertEquals(listOf(record.baseline), baselineStore.load())
    }

    @Test fun missingFileIsNotAnError() { assertNull(SessionRecoveryStore(directory.root).load()) }

    @Test fun malformedOrUnsupportedFileIsRejected() {
        val file = File(directory.root, "session-recovery.json")
        for (bad in listOf("{", """{"version":99,"session":null}""")) {
            file.writeText(bad)
            assertThrows(Exception::class.java) { SessionRecoveryStore(directory.root).load() }
            assertEquals(bad, file.readText())
        }
    }

    @Test fun incompleteAtomicWriteKeepsCommittedCheckpoint() {
        val store = SessionRecoveryStore(directory.root)
        store.save(record)
        File(directory.root, "session-recovery.json.new").writeText("{")
        assertEquals(record, store.load())
    }

    @Test fun simulationExpiredAndFutureCheckpointsCannotResume() {
        assertTrue(record.canRestore(3000))
        assertFalse(record.canRestore(1999))
        assertFalse(record.canRestore(record.savedAtMs + SessionRecovery.MAX_AGE_MS + 1))
        assertFalse(record.copy(baseline = record.baseline.copy(simulation = true)).canRestore(3000))
        assertFalse(record.copy(interruptedAtMs = 3000).canRestore(3000))
    }

    @Test fun restorationCountsUncertainGapSeparatelyFromKnownPauses() {
        val restored = record.restoredSession(4000)
        assertEquals(record.session.id, restored.id)
        assertEquals(2, restored.recoveries)
        assertEquals(4100L, restored.uncertainMs)
        assertEquals(1000L, restored.pausedMs)
        assertEquals(4000L, record.copy(interruptedAtMs = null).restoredSession(4000).uncertainMs)
    }
}
