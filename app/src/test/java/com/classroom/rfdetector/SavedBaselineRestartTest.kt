package com.classroom.rfdetector

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Looper
import android.os.SystemClock
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.SignalSnapshot
import com.classroom.rfdetector.core.SnapshotSignal
import com.classroom.rfdetector.core.SnapshotStore
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/**
 * Recreates the ViewModel against the same files, using synthetic Android Wi-Fi
 * broadcasts rather than RadioSimulator. This checks persistence and restart
 * state, not OS process recreation, physical reception or exam-room accuracy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavedBaselineRestartTest {
    @get:Rule val directory = TemporaryFolder()

    private val app = RuntimeEnvironment.getApplication()
    private val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val instances = mutableListOf<AppViewModel>()
    private val knownAddress = "10:20:30:40:50:60"
    private val newAddress = "A0:B0:C0:D0:E0:F0"

    private fun reopen(): AppViewModel {
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = directory.root
        }
        return AppViewModel(context).also { vm ->
            instances += vm
            awaitStorage(vm)
        }
    }

    @After fun close() {
        instances.forEach { it.shutdown() }
    }

    private fun awaitStorage(vm: AppViewModel) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!vm.snapshotBusy.value) return
            Thread.sleep(5)
        }
        fail("Snapshot operation did not finish")
    }

    private fun receive(vararg devices: Pair<String, String>) {
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        shadowOf(wifi).setScanResults(devices.map { (address, name) ->
            ScanResult().apply {
                BSSID = address
                SSID = name
                level = -60
                frequency = 2437
                timestamp = SystemClock.elapsedRealtime() * 1000
            }
        })
        app.sendBroadcast(Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            .putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
    }

    private fun record() = SignalSnapshot("시험실", 1_000L, false, listOf(
        SnapshotSignal(knownAddress, ScanSource.WIFI, "Classroom AP", -60.0, "unknown"),
    ))

    @Test fun savedBaselineSurvivesReopeningAndJudgesOnlyFreshObservations() {
        val before = reopen()
        before.startScan()
        receive(knownAddress to "Classroom AP")
        assertEquals(listOf(knownAddress), before.devices.value.map { it.key })
        before.takeBaseline()
        awaitStorage(before)
        assertTrue(before.baselineSet.value)
        val baseline = before.baselineSnapshot.value!!
        val oldSession = before.scanSession.value!!.id
        before.acknowledge("WIFI:name:exam hotspot")
        before.shutdown()

        val after = reopen()
        assertEquals(baseline, after.savedBaseline.value)
        assertFalse(after.scanning.value)
        assertNull(after.scanSession.value)
        assertTrue(after.devices.value.isEmpty())
        assertTrue(after.acknowledged.value.isEmpty())

        after.useSavedBaseline()
        after.startScan()
        assertTrue(after.baselineSet.value)
        assertEquals(baseline, after.baselineSnapshot.value)
        assertNotEquals(oldSession, after.scanSession.value!!.id)
        assertTrue("Saved signals must not become live observations", after.devices.value.isEmpty())

        receive(knownAddress to "Classroom AP", newAddress to "Exam hotspot")
        assertEquals(setOf(knownAddress, newAddress), after.devices.value.map { it.key }.toSet())
        assertEquals(listOf(newAddress), after.watch.value.wifi.single().devices.map { it.key })
        assertEquals(listOf(baseline), SnapshotStore(directory.root).load())
    }

    @Test fun reusedBaselineSurvivesBackgroundPauseButManualStopDoesNotAutoResume() {
        val baseline = record()
        SnapshotStore(directory.root).save(listOf(baseline))
        val vm = reopen()
        vm.useSavedBaseline()
        vm.startScan()
        val sessionId = vm.scanSession.value!!.id

        vm.pauseForBackground()
        assertFalse(vm.scanning.value)
        vm.resumeFromBackground()
        assertTrue(vm.scanning.value)
        assertEquals(sessionId, vm.scanSession.value!!.id)
        assertEquals(baseline, vm.baselineSnapshot.value)
        assertTrue(vm.baselineSet.value)
        assertEquals(1, vm.scanSession.value!!.pauses)

        vm.stopScan()
        vm.resumeFromBackground()
        assertFalse(vm.scanning.value)
        vm.startScan()
        assertNotEquals(sessionId, vm.scanSession.value!!.id)
        assertTrue(vm.baselineSet.value)
        assertEquals(listOf(baseline), SnapshotStore(directory.root).load())
    }

    @Test fun newerSimulationRecordDoesNotReplaceReusableRealBaseline() {
        val real = record()
        SnapshotStore(directory.root).save(listOf(real, real.copy(capturedAtMs = 2_000L, simulation = true)))

        val vm = reopen()
        assertEquals(real, vm.savedBaseline.value)
        vm.useSavedBaseline()
        vm.startScan()
        assertTrue(vm.baselineSet.value)
        assertEquals(real, vm.baselineSnapshot.value)
    }

    @Test fun unreadableRecordIsReportedWithoutOverwritingTheOriginal() {
        val file = File(directory.root, "signal-snapshots.json")
        val original = """{"version":99,"snapshots":[]}"""
        file.writeText(original)

        val vm = reopen()
        assertFalse(vm.snapshotBusy.value)
        assertNull(vm.savedBaseline.value)
        assertNotNull(vm.snapshotError.value)
        vm.useSavedBaseline()
        assertNull(vm.baselineSnapshot.value)
        assertFalse(vm.baselineSet.value)
        assertFalse(vm.scanning.value)
        assertEquals(original, file.readText())
    }
}
