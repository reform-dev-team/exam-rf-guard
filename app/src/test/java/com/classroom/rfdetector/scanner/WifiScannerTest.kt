package com.classroom.rfdetector.scanner

import android.content.Context
import android.content.Intent
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Looper
import android.os.SystemClock
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanState
import java.time.Duration
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowWifiManager
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WifiScannerTest {
    private val app = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scanner = WifiScanner(app, scope)
    private val observations = mutableListOf<RfObservation>()

    @After fun close() { scanner.stop(); scope.cancel() }

    private fun result(address: String, ssid: String?, ageMs: Long = 0) = ScanResult().apply {
        BSSID = address
        SSID = ssid
        level = -60
        frequency = 2437
        timestamp = (SystemClock.elapsedRealtime() - ageMs) * 1000
    }

    private fun complete(success: Boolean = true) {
        app.sendBroadcast(Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION).putExtra(WifiManager.EXTRA_RESULTS_UPDATED, success))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun healthDistinguishesWaitingFreshEmptyCachedAndStoppedWithoutChangingCounts() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
        shadowOf(manager).setScanResults(listOf(result("old", "old", 1_000)))
        assertEquals(ScanState.STOPPED, scanner.health(System.currentTimeMillis()).state)
        scanner.start(onObs = { observations.add(it) })
        assertEquals(ScanState.WAITING, scanner.health(System.currentTimeMillis()).state)
        complete(false)
        assertEquals(ScanState.WAITING, scanner.health(System.currentTimeMillis()).state)
        assertEquals(0, scanner.completedScans)
        complete()
        assertEquals(1, scanner.completedScans)
        assertEquals(ScanState.WAITING, scanner.health(System.currentTimeMillis()).state)
        assertTrue(observations.isEmpty())
        shadowOf(manager).setScanResults(listOf(result("new", "new")))
        complete()
        assertEquals(2, scanner.completedScans)
        assertEquals(ScanState.COMPLETED, scanner.health(System.currentTimeMillis()).state)
        assertEquals(1, observations.size)
        val completed = scanner.health(System.currentTimeMillis()).lastObservedAtMs!!
        assertEquals(ScanState.WAITING, scanner.health(completed + 90_001).state)
        complete()
        assertEquals(3, scanner.completedScans)
        assertEquals(ScanState.WAITING, scanner.health(System.currentTimeMillis()).state)
        shadowOf(manager).setScanResults(emptyList())
        complete()
        assertEquals(4, scanner.completedScans)
        assertEquals(ScanState.COMPLETED, scanner.health(System.currentTimeMillis()).state)
        assertEquals(1, observations.size)
        scanner.stop()
        assertEquals(ScanState.STOPPED, scanner.health(System.currentTimeMillis()).state)
        scanner.start(onObs = { observations.add(it) })
        assertEquals(ScanState.WAITING, scanner.health(System.currentTimeMillis()).state)
        assertEquals(0, scanner.completedScans)
    }

    @Test
    @Config(shadows = [PermissionDeniedWifiManager::class])
    fun permissionFailureStopsRequestsAndIgnoresCompletions() {
        PermissionDeniedWifiManager.calls = 0
        scanner.start(onObs = { observations.add(it) })
        assertFalse(scanner.active)
        assertEquals(ScanState.ERROR, scanner.health(System.currentTimeMillis()).state)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        assertEquals(1, PermissionDeniedWifiManager.calls)
        complete()
        assertEquals(0, scanner.completedScans)
        scanner.stop()
        assertEquals(ScanState.STOPPED, scanner.health(System.currentTimeMillis()).state)
    }

    @Implements(WifiManager::class)
    class PermissionDeniedWifiManager : ShadowWifiManager() {
        companion object { var calls = 0 }
        @Implementation
        override fun startScan(): Boolean {
            calls++
            throw SecurityException("denied")
        }
    }

    @Test fun hiddenSsidCanBeNullAndKeepsActualObservationTime() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
        shadowOf(manager).setScanResults(emptyList())
        scanner.start(onObs = { observations.add(it) })
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))
        shadowOf(manager).setScanResults(listOf(result("00:11:22:33:44:55", null, 5_000)))
        complete()
        val observation = observations.single()
        assertNull(observation.name)
        assertTrue(observation.hidden)
        val ageMs = System.currentTimeMillis() - observation.timestampMs
        assertTrue("Cached result must retain its five-second age, was $ageMs ms", ageMs in 5_000..5_500)
    }

    @Test fun expiredAndFutureScanEntriesAreExcluded() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
        shadowOf(manager).setScanResults(listOf(result("fresh", "fresh"), result("expired", "expired", 90_001), result("future", "future", -1_000)))
        scanner.start(onObs = { observations.add(it) })
        complete()
        assertEquals(listOf("fresh"), observations.map { it.key })
    }

    @Test fun failedCompletionDoesNotReprocessCachedResults() {
        shadowOf(manager).setScanResults(listOf(result("cached", "cached")))
        scanner.start(onObs = { observations.add(it) })
        complete(false)
        assertTrue(observations.isEmpty())
        assertEquals(0, scanner.completedScans)
        assertNotNull(scanner.lastError)
    }

    @Test fun stopUnregistersReceiver() {
        shadowOf(manager).setScanResults(emptyList())
        scanner.start(onObs = { observations.add(it) })
        complete()
        scanner.stop()
        shadowOf(manager).setScanResults(listOf(result("after-stop", "after-stop")))
        complete()
        assertTrue(observations.isEmpty())
    }

    @Test fun duplicateAndOlderNotificationsDoNotCountAsNewSignalSamples() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
        val original = result("same-ap", "same-ap")
        shadowOf(manager).setScanResults(listOf(original))
        scanner.start(onObs = { observations.add(it) })
        complete()
        complete()
        assertEquals(1, observations.size)
        shadowOf(manager).setScanResults(listOf(result("same-ap", "same-ap", 10_000)))
        complete()
        assertEquals(1, observations.size)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        shadowOf(manager).setScanResults(listOf(result("same-ap", "same-ap")))
        complete()
        assertEquals(2, observations.size)
        // Robolectric's elapsed clock can advance independently of the JVM wall clock.
        assertTrue(kotlin.math.abs(System.currentTimeMillis() - observations.last().timestampMs) < 1000)
    }

    @Test fun cachedFramesFromBeforeThisSessionAreNotAssignedToTheNewRoom() {
        ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
        shadowOf(manager).setScanResults(listOf(result("previous-room", "old AP", 1000)))
        scanner.start(onObs = { observations.add(it) })
        complete()
        assertTrue(observations.isEmpty())
    }

    @Test fun queuedBroadcastForStoppedReceiverDoesNotRunInRestartedSession() {
        shadowOf(manager).setScanResults(emptyList())
        scanner.start(onObs = { observations.add(it) })
        app.sendBroadcast(Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION).putExtra(WifiManager.EXTRA_RESULTS_UPDATED, true))
        scanner.stop()
        scanner.start(onObs = { observations.add(it) })
        shadowOf(manager).setScanResults(listOf(result("new-room", "new AP")))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(observations.isEmpty())
        complete()
        assertEquals(listOf("new-room"), observations.map { it.key })
    }

    @Test fun successfulEmptyCompletionClearsErrorButIsNotInventedDeviceReception() {
        shadowOf(manager).setScanResults(emptyList())
        scanner.start(onObs = { observations.add(it) })
        assertTrue(scanner.active)
        complete(false)
        assertNotNull(scanner.lastError)
        assertEquals(0, scanner.completedScans)
        complete()
        assertNull(scanner.lastError)
        assertEquals(1, scanner.completedScans)
        assertTrue(observations.isEmpty())
        scanner.stop()
        assertFalse(scanner.active)
        scanner.start(onObs = { observations.add(it) })
        assertEquals(0, scanner.completedScans)
    }
}

