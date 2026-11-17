package com.classroom.rfdetector

import android.content.ContextWrapper
import android.os.Looper
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.acknowledgeKey
import com.classroom.rfdetector.core.WEAK_SIGNAL_DBM
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
class SimultaneousRetentionTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var vm: AppViewModel
    private var clockNow = 1_000_000L

    @Before fun create() {
        val app = RuntimeEnvironment.getApplication()
        vm = AppViewModel(object : ContextWrapper(app) {
            override fun getFilesDir() = directory.root
        }, nowMs = { clockNow })
        awaitStorage()
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

    private fun advanceTime(milliseconds: Long) {
        var remaining = milliseconds
        while (remaining > 0) {
            val step = minOf(250L, remaining)
            clockNow += step
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            remaining -= step
        }
    }

    @Test fun simultaneousHiddenWifiCandidatesRemainIndependentlyReviewable() {
        vm.takeBaseline()
        awaitStorage()
        assertTrue(vm.baselineSet.value)
        assertEquals(0, vm.watch.value.count)
        assertEquals(5, vm.watch.value.knownGroups.size)
        vm.setSurveyOpen(true)

        val first = "12:34:56:78:90:AA"
        val second = "12:34:56:78:90:AB"
        val weak = "12:34:56:78:90:AC"
        val now = clockNow
        vm.tracker.update(RfObservation(first, ScanSource.WIFI, null, -55, null, false, now, details = mapOf("test" to "first")))
        vm.tracker.update(RfObservation(second, ScanSource.WIFI, null, -35, null, false, now, details = mapOf("test" to "second")))
        vm.tracker.update(RfObservation(weak, ScanSource.WIFI, null, -100, null, false, now, details = mapOf("test" to "weak")))
        advanceTime(100)

        val group = vm.watch.value.wifi.single { group -> group.devices.any { it.key == first } }
        assertEquals(setOf(first, second), group.devices.filter { (it.rssiEma ?: -127.0) >= WEAK_SIGNAL_DBM }.map { it.key }.toSet())
        val firstKey = group.devices.single { it.key == first }.acknowledgeKey
        val secondKey = group.devices.single { it.key == second }.acknowledgeKey
        assertNotEquals(firstKey, secondKey)
        assertEquals(setOf(first, second), vm.surveyNotices.value.map { it.key }.toSet())
        assertEquals("first", vm.surveyNotices.value.single { it.key == first }.details["test"])
        assertEquals("second", vm.surveyNotices.value.single { it.key == second }.details["test"])

        advanceTime(100_000)
        assertTrue(vm.watch.value.wifi.none { group -> group.devices.any { it.key == first || it.key == second } })
        assertEquals(setOf(first, second), vm.surveyNotices.value.map { it.key }.toSet())
        assertEquals("first", vm.surveyNotices.value.single { it.acknowledgeKey == firstKey }.details["test"])
        assertEquals("second", vm.surveyNotices.value.single { it.acknowledgeKey == secondKey }.details["test"])
        vm.acknowledge(firstKey)
        assertEquals(listOf(secondKey), vm.surveyNotices.value.map { it.acknowledgeKey })
        vm.acknowledge(secondKey)
        assertTrue(vm.surveyNotices.value.isEmpty())
    }
}
