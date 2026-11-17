package com.classroom.rfdetector

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.net.wifi.WifiManager
import android.os.Looper
import android.provider.Settings
import com.classroom.rfdetector.core.*
import java.io.File
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionRecoveryTest {
    @get:Rule val directory = TemporaryFolder()
    private val app = RuntimeEnvironment.getApplication()
    private val instances = mutableListOf<AppViewModel>()
    private val store get() = SessionRecoveryStore(directory.root)
    private fun record(): SessionRecovery {
        val now = System.currentTimeMillis()
        return SessionRecovery(ScanSession("original-session", now - 60_000, "시험실"),
            SignalSnapshot("시험실", now - 50_000, false, listOf(
                SnapshotSignal("10:20:30:40:50:60", ScanSource.WIFI, "Classroom AP", -60.0, "unknown"))),
            setOf("BLE:name:earbuds"), now - 10_000)
    }
    @Before fun prepareRadios() {
        shadowOf(app).grantPermissions("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.NEARBY_WIFI_DEVICES")
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_ON)
        (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled = true
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
        shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).setNotificationPolicyAccessGranted(true)
    }
    private fun reopen(): AppViewModel = AppViewModel(object : ContextWrapper(app) {
        override fun getFilesDir() = directory.root
    }).also { instances += it; awaitStorage(it) }

    private fun awaitStorage(vm: AppViewModel) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!vm.snapshotBusy.value && !vm.recoveryLoading.value && vm.recoveryPendingWrites.value == 0) return
            Thread.sleep(5)
        }
        fail("Recovery storage did not finish")
    }
    @After fun close() { instances.forEach { it.shutdown(); awaitStorage(it) } }

    @Test fun reopeningRequiresConfirmationAndRestoresDecisionsWithFreshReceiverState() {
        val checkpoint = record()
        store.save(checkpoint)
        // A newer separately saved baseline must not replace the session's actual baseline.
        SnapshotStore(directory.root).save(listOf(checkpoint.baseline.copy(area = "다른 교실")))
        val vm = reopen()
        assertEquals(checkpoint, vm.pendingRecovery.value)
        assertFalse(vm.scanning.value)
        assertNull(vm.scanSession.value)
        assertTrue(vm.acknowledged.value.isEmpty())
        assertTrue(vm.restoreSession())
        assertTrue(vm.scanning.value)
        assertTrue(vm.baselineSet.value)
        assertEquals(checkpoint.baseline, vm.baselineSnapshot.value)
        assertEquals(checkpoint.acknowledged, vm.acknowledged.value)
        assertEquals(checkpoint.session.id, vm.scanSession.value!!.id)
        assertEquals(1, vm.scanSession.value!!.recoveries)
        assertTrue(vm.scanSession.value!!.uncertainMs >= 10_000)
        assertTrue(vm.devices.value.isEmpty())
        assertEquals(0, vm.watch.value.count)
        assertNull(vm.pendingRecovery.value)
        awaitStorage(vm)
        assertEquals(checkpoint.acknowledged, store.load()!!.acknowledged)
    }

    @Test fun acknowledgementAndShutdownRemainRecoverableButNormalStopDoesNot() {
        store.save(record())
        val before = reopen()
        assertTrue(before.restoreSession())
        before.acknowledge("WIFI:name:proctor hotspot")
        awaitStorage(before)
        assertTrue(store.load()!!.acknowledged.contains("WIFI:name:proctor hotspot"))
        before.shutdown()
        awaitStorage(before)
        val after = reopen()
        assertNotNull(after.pendingRecovery.value)
        assertTrue(after.restoreSession())
        assertEquals(2, after.scanSession.value!!.recoveries)
        after.stopScan()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL,
            (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter)
        awaitStorage(after)
        after.shutdown()
        awaitStorage(after)
        assertNull(store.load())
        assertNull(reopen().pendingRecovery.value)
    }

    @Test fun stopWhilePausedCannotBeResurrectedByResumeOrQueuedWrites() {
        store.save(record())
        val vm = reopen()
        assertTrue(vm.restoreSession())
        vm.acknowledge("BLE:name:another")
        vm.pauseForBackground()
        vm.stopScan()
        vm.resumeFromBackground()
        awaitStorage(vm)
        assertFalse(vm.scanning.value)
        assertNull(store.load())
    }

    @Test fun missingPermissionDoesNotConsumeRecoveryOrStartScanning() {
        store.save(record())
        val vm = reopen()
        shadowOf(app).denyPermissions("android.permission.ACCESS_FINE_LOCATION")
        assertFalse(vm.restoreSession())
        assertFalse(vm.scanning.value)
        assertNotNull(vm.pendingRecovery.value)
        assertNotNull(vm.recoveryError.value)
    }

    @Test fun discardKeepsReusableBaselineButDoesNotRestoreConfirmations() {
        val checkpoint = record()
        SnapshotStore(directory.root).save(listOf(checkpoint.baseline))
        store.save(checkpoint)
        val vm = reopen()
        vm.discardRecovery()
        awaitStorage(vm)
        assertNull(store.load())
        assertEquals(checkpoint.baseline, vm.savedBaseline.value)
        vm.useSavedBaseline()
        vm.startScan()
        assertTrue(vm.acknowledged.value.isEmpty())
        assertNotEquals(checkpoint.session.id, vm.scanSession.value!!.id)
    }

    @Test fun expiredOrSimulationRecoveryIsNotOffered() {
        for (checkpoint in listOf(record().let { it.copy(savedAtMs = System.currentTimeMillis() - SessionRecovery.MAX_AGE_MS - 1) },
            record().let { it.copy(baseline = it.baseline.copy(simulation = true)) })) {
            store.save(checkpoint)
            val vm = reopen()
            assertNull(vm.pendingRecovery.value)
            assertNull(store.load())
            vm.shutdown()
            awaitStorage(vm)
        }
    }

    @Test fun restoredHiddenWifiAcknowledgementStillExcludesOnlyItsOwnAddress() {
        val confirmed = "A0:B1:C2:D3:E4:F1"
        val fresh = "A0:B1:C2:D3:E4:F2"
        val key = "WIFI:hidden:$confirmed"
        store.save(record().copy(acknowledged = setOf(key)))
        val vm = reopen()
        assertTrue(vm.restoreSession())
        val now = System.currentTimeMillis()
        for (address in listOf(confirmed, fresh)) vm.tracker.update(RfObservation(
            address, ScanSource.WIFI, null, -60, null, true, now))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        assertEquals(setOf(key), vm.acknowledged.value)
        assertEquals(listOf(fresh), vm.watch.value.wifi.single().devices.map { it.key })
    }

    @Test fun damagedRecoveryDoesNotBlockLoadingTheIndependentBaseline() {
        val baseline = record().baseline
        SnapshotStore(directory.root).save(listOf(baseline))
        File(directory.root, "session-recovery.json").writeText("{")
        val vm = reopen()
        assertNull(vm.pendingRecovery.value)
        assertNotNull(vm.recoveryError.value)
        assertEquals(baseline, vm.savedBaseline.value)
        vm.useSavedBaseline()
        vm.startScan()
        assertTrue(vm.scanning.value)
    }
}
