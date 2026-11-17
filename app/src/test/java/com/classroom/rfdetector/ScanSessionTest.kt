package com.classroom.rfdetector

import android.content.ContextWrapper
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.groupKey
import com.classroom.rfdetector.core.acknowledgeKey
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanSessionTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var vm: AppViewModel
    private var clockNow = 1_000_000L

    private fun newViewModel(): AppViewModel {
        val app = RuntimeEnvironment.getApplication()
        return AppViewModel(object : ContextWrapper(app) { override fun getFilesDir() = directory.root }, nowMs = { clockNow }).also { vm = it; awaitStorage() }
    }

    @Before fun create() {
        newViewModel()
        vm.setSim(true)
        vm.startScan()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(5, vm.devices.value.size)
    }
    @After fun close() { vm.shutdown() }

    private fun awaitStorage() {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!vm.snapshotBusy.value) return
            Thread.sleep(5)
        }
        fail("Snapshot operation did not finish")
    }

    private fun saveEmptyRoom() {
        vm.takeBaseline()
        awaitStorage()
        assertTrue(vm.baselineSet.value)
    }

    private fun advanceTime(milliseconds: Long) {
        var remaining = milliseconds
        while (remaining > 0) {
            val step = minOf(250L, remaining)
            clockNow += step
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            remaining -= step
        }
    }

    @Test fun emptyRoomRecordStartsWatchingAndOnlyNewKindsAreFlagged() {
        assertFalse(vm.baselineSet.value)
        assertEquals(0, vm.watch.value.count)
        saveEmptyRoom()
        assertEquals(0, vm.watch.value.count)
        assertEquals(5, vm.watch.value.knownGroups.size)

        vm.simAddDevice()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        val group = vm.watch.value.bluetooth.single()
        assertEquals(0, group.baselineCount)

        vm.acknowledge(group.key)
        assertEquals(0, vm.watch.value.count)
    }

    @Test fun leavingAndReturningContinuesTheSameSessionAndCountsTheGap() {
        saveEmptyRoom()
        val session = vm.scanSession.value!!
        vm.acknowledge("BLE:name:anything")
        vm.pauseForBackground()
        assertFalse(vm.scanning.value)
        vm.resumeFromBackground()
        assertTrue(vm.scanning.value)
        assertEquals(session.id, vm.scanSession.value!!.id)
        assertEquals(1, vm.scanSession.value!!.pauses)
        assertEquals(5, vm.devices.value.size)
        assertTrue(vm.baselineSet.value)
        assertEquals(1, vm.acknowledged.value.size)

        vm.stopScan()
        vm.resumeFromBackground()
        assertFalse("A manual stop stays stopped", vm.scanning.value)
        vm.startScan()
        assertNotEquals(session.id, vm.scanSession.value!!.id)
        assertTrue("The same empty-room record keeps being used", vm.baselineSet.value)
        assertTrue(vm.acknowledged.value.isEmpty())
    }

    @Test fun restartingFromTheEmptyRoomClearsTheRecordAndCollectsAgain() {
        saveEmptyRoom()
        val session = vm.scanSession.value!!.id
        vm.restartFromEmptyRoom()
        assertTrue(vm.scanning.value)
        assertFalse(vm.baselineSet.value)
        assertNull(vm.baselineSnapshot.value)
        assertNotEquals(session, vm.scanSession.value!!.id)
    }

    @Test fun surveyNoticesAreBoundedDeduplicatedAndRespectAcknowledgement() {
        saveEmptyRoom()
        vm.setSurveyOpen(true)
        val now = clockNow
        repeat(55) { i ->
            vm.tracker.update(RfObservation("AA:BB:CC:DD:EE:%02X".format(i), ScanSource.WIFI,
                "candidate-$i", -55, null, false, now, details = mapOf("test" to "detail-$i")))
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(50, vm.surveyNotices.value.size)
        val device = vm.surveyNotices.value.last()
        assertEquals("detail-${device.displayName.substringAfter('-')}", device.details["test"])
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(50, vm.surveyNotices.value.map { it.trackingKey }.distinct().size)
        vm.acknowledge(device.groupKey)
        assertTrue(vm.surveyNotices.value.none { it.groupKey == device.groupKey })
        vm.setSurveyOpen(false)
        val remaining = vm.surveyNotices.value.first()
        vm.dismissSurveyNotice(remaining.trackingKey)
        assertTrue(vm.surveyNotices.value.none { it.trackingKey == remaining.trackingKey })
    }

    @Test fun hiddenWifiNoticesKeepSeparateAddressAcknowledgements() {
        saveEmptyRoom()
        vm.setSurveyOpen(true)
        val first = "12:34:56:78:90:AA"
        val second = "12:34:56:78:90:AB"
        vm.tracker.update(RfObservation(first, ScanSource.WIFI, null, -55, null, false, clockNow))
        advanceTime(250)
        val firstNotice = vm.surveyNotices.value.single { it.key == first }
        vm.tracker.update(RfObservation(second, ScanSource.WIFI, null, -35, null, false, clockNow))
        advanceTime(250)
        assertEquals(setOf(first, second), vm.surveyNotices.value.map { it.key }.toSet())
        vm.acknowledge(firstNotice.acknowledgeKey)
        assertEquals(listOf(second), vm.surveyNotices.value.map { it.key })
        assertTrue(vm.watch.value.wifi.any { group -> group.devices.any { it.key == second } })
        vm.acknowledge(vm.surveyNotices.value.single().acknowledgeKey)
        assertTrue(vm.surveyNotices.value.isEmpty())
        assertEquals(0, vm.watch.value.count)
    }

    @Test fun increasedKnownGroupProducesOneRepresentativeNotice() {
        saveEmptyRoom()
        vm.setSurveyOpen(true)
        val extra = RfObservation("AA:BB:CC:DD:EE:01", ScanSource.BLE,
            "AirPods Pro (가상)", -50, null, false, clockNow)
        repeat(32) {
            vm.tracker.update(extra.copy(timestampMs = clockNow))
            advanceTime(1_000)
        }
        val group = vm.watch.value.bluetooth.single { it.strongest.displayName == "AirPods Pro (가상)" }
        assertEquals(2, group.devices.size)
        assertEquals(listOf(group.strongest.trackingKey), vm.surveyNotices.value.map { it.trackingKey })
    }

    @Test fun quietWeakAndFindMySignalsDoNotBecomeSurveyWarnings() {
        saveEmptyRoom()
        vm.setSurveyOpen(true)
        val now = clockNow
        vm.tracker.update(RfObservation("12:34:56:78:90:AB", ScanSource.WIFI, "quiet", -100, null, false, now))
        vm.tracker.update(RfObservation("12:34:56:78:90:AC", ScanSource.BLE, null, -50, null, false, now,
            hints = setOf(com.classroom.rfdetector.core.SignalHint.APPLE_FIND_MY)))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue(vm.watch.value.far.any { it.strongest.key == "12:34:56:78:90:AB" })
        assertTrue(vm.watch.value.findMy.any { it.key == "12:34:56:78:90:AC" })
        assertEquals(0, vm.watch.value.count)
        assertTrue(vm.surveyNotices.value.isEmpty())
    }

    @Test fun surveyNoticesSurvivePauseAndStopButNotNewSessionOrBaselineReset() {
        saveEmptyRoom()
        vm.setSurveyOpen(true)
        val now = clockNow
        vm.tracker.update(RfObservation("12:34:56:78:90:AB", ScanSource.WIFI, "transient", -55, null, false, now))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(1, vm.surveyNotices.value.size)
        vm.pauseForBackground()
        advanceTime(100_000)
        vm.resumeFromBackground()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(1, vm.surveyNotices.value.size)
        assertEquals(0, vm.watch.value.wifi.size)
        vm.stopScan()
        assertEquals(1, vm.surveyNotices.value.size)
        vm.startScan()
        assertTrue(vm.surveyNotices.value.isEmpty())
        vm.tracker.update(RfObservation("12:34:56:78:90:AC", ScanSource.WIFI, "another", -55, null, false, clockNow))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertEquals(1, vm.surveyNotices.value.size)
        vm.resetBaseline()
        assertTrue(vm.surveyNotices.value.isEmpty())
    }

    @Test fun backgroundGapAndClosedSurveyDoNotCreateNotices() {
        saveEmptyRoom()
        vm.setSurveyOpen(true)
        vm.pauseForBackground()
        vm.tracker.update(RfObservation("12:34:56:78:90:AB", ScanSource.WIFI, "gap", -55, null, false, clockNow))
        advanceTime(1_000)
        vm.resumeFromBackground()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue(vm.surveyNotices.value.isEmpty())
        vm.setSurveyOpen(false)
        vm.tracker.update(RfObservation("12:34:56:78:90:AC", ScanSource.WIFI, "closed", -55, null, false, clockNow))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        assertTrue(vm.surveyNotices.value.isEmpty())
    }

    @Test fun onlyTheLatestRealEmptyRoomRecordIsOfferedAfterReopening() {
        saveEmptyRoom()
        vm.shutdown()
        // 가상 전파로 저장한 기록은 실제 감시에 이어 쓰지 않는다.
        assertNull(newViewModel().savedBaseline.value)
    }
}
