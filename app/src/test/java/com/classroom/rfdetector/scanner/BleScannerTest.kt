package com.classroom.rfdetector.scanner

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.SystemClock
import com.classroom.rfdetector.core.DeviceTracker
import com.classroom.rfdetector.core.DistanceClass
import com.classroom.rfdetector.core.RfObservation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BleScannerTest {
    @Before fun clockHasStarted() { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }

    @Test fun advertisementPreservesCompanyAndNameWithoutTreatingRadiatedPowerAsCalibration() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        assertTrue(scanner.start { observations.add(it) })
        val name = "AirPods Pro".toByteArray()
        val bytes = byteArrayOf(2, 1, 6, 2, 10, 0, 5, -1, 76, 0, 1, 2) +
            byteArrayOf((name.size + 1).toByte(), 9) + name + byteArrayOf(0)
        val record = ReflectionHelpers.callStaticMethod<ScanRecord>(ScanRecord::class.java, "parseFromBytes", ClassParameter.from(ByteArray::class.java, bytes))
        assertEquals(0, record.txPowerLevel)
        val result = ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), record, -59, SystemClock.elapsedRealtimeNanos())
        shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single().onScanResult(1, result)
        val observation = observations.single()
        assertEquals("AirPods Pro", observation.name)
        assertEquals(setOf(76), observation.manufacturerIds)
        assertNull(observation.txPower)
        val tracker = DeviceTracker()
        tracker.update(observation)
        assertEquals(DistanceClass.VERY_CLOSE, tracker.snapshot(observation.timestampMs).single().distanceClass)
        scanner.stop()
        assertTrue(shadowOf(adapter.bluetoothLeScanner).scanCallbacks.isEmpty())
    }

    @Test fun advertisementKindHintsAreReadAndLongScanIsRestartedWithTheSameCallback() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        assertTrue(scanner.start { observations.add(it) })
        val callback = shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single()
        val bytes = byteArrayOf(2, 1, 6, 3, 0x19, 0xC1.toByte(), 0, 7, -1, 76, 0, 0x10, 2, 1, 0x18)
        val record = ReflectionHelpers.callStaticMethod<ScanRecord>(ScanRecord::class.java, "parseFromBytes", ClassParameter.from(ByteArray::class.java, bytes))
        callback.onScanResult(1, ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), record, -59, SystemClock.elapsedRealtimeNanos()))
        assertEquals(setOf(com.classroom.rfdetector.core.SignalHint.WATCH, com.classroom.rfdetector.core.SignalHint.APPLE_ACTIVE), observations.single().hints)

        // 실제 기기는 5분 뒤 탐색을 낮춘다. 그 전에 다시 걸리는지, 신호가 끊긴 채로도 계속 살아 있는지 본다.
        val scans = shadowOf(adapter.bluetoothLeScanner)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertSame(callback, scans.scanCallbacks.single())
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMinutes(26))
        assertTrue(scanner.active)
        assertSame(callback, shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single())
        callback.onScanResult(1, ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), null, -59, SystemClock.elapsedRealtimeNanos()))
        assertEquals(2, observations.size)
        scanner.stop()
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMinutes(26))
        assertTrue(shadowOf(adapter.bluetoothLeScanner).scanCallbacks.isEmpty())
    }

    @Test fun batchResultsKeepCaptureTimeAndStoppedCallbacksAreIgnored() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(10))
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        scanner.start { observations.add(it) }
        val callback = shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        val capturedAtNanos = android.os.SystemClock.elapsedRealtimeNanos() - 2_000_000_000L
        val result = ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), null, -80, capturedAtNanos)
        callback.onBatchScanResults(mutableListOf(result))
        assertEquals(1, observations.size)
        assertTrue(System.currentTimeMillis() - observations.single().timestampMs in 2_000..2_500)
        scanner.stop()
        callback.onScanResult(1, result)
        assertEquals(1, observations.size)
        scanner.start { observations.add(it) }
        callback.onBatchScanResults(mutableListOf(result))
        assertEquals(1, observations.size)
        scanner.stop()
    }

    @Test fun failedScanIsNotReportedActiveAndRestartHasFreshHealth() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        scanner.start { observations.add(it) }
        assertTrue(scanner.active)
        assertNull(scanner.lastReceivedAtMs)
        val failedCallback = shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single()
        failedCallback.onScanFailed(2)
        assertFalse(scanner.active)
        assertTrue(scanner.lastError!!.contains("(2)"))
        failedCallback.onScanResult(1, ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), null, -60, SystemClock.elapsedRealtimeNanos()))
        assertTrue(observations.isEmpty())
        assertNull(scanner.lastReceivedAtMs)

        assertTrue(scanner.start { observations.add(it) })
        assertTrue(scanner.active)
        assertNull(scanner.lastError)
        failedCallback.onScanFailed(3)
        assertTrue(scanner.active)
        assertNull(scanner.lastError)
        shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single().onScanResult(
            1, ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), null, -60, SystemClock.elapsedRealtimeNanos()))
        assertEquals(observations.single().timestampMs, scanner.lastReceivedAtMs)
        scanner.stop()
        assertFalse(scanner.active)
    }

    @Test fun bluetoothDisabledReportsReasonWithoutPretendingToScan() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(false)
        val scanner = BleScanner(context)
        assertFalse(scanner.start { fail("Disabled bluetooth cannot receive") })
        assertFalse(scanner.active)
        assertNotNull(scanner.lastError)
        assertNull(scanner.lastReceivedAtMs)
    }

    @Test fun preSessionFutureAndMissingTimestampsDoNotBecomeFreshObservations() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        val oldTime = SystemClock.elapsedRealtimeNanos()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        scanner.start { observations.add(it) }
        val callback = shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single()
        val device = adapter.getRemoteDevice("00:11:22:33:44:55")
        callback.onBatchScanResults(mutableListOf(
            ScanResult(device, null, -60, oldTime),
            ScanResult(device, null, -60, SystemClock.elapsedRealtimeNanos() + 1_000_000_000),
            ScanResult(device, null, -60, 0),
            ScanResult(device, null, -60, -1),
        ))
        assertTrue(observations.isEmpty())
        assertNull(scanner.lastReceivedAtMs)
        assertTrue(scanner.active)

        callback.onScanResult(1, ScanResult(device, null, -60, SystemClock.elapsedRealtimeNanos()))
        assertEquals(1, observations.size)
        assertEquals(observations.single().timestampMs, scanner.lastReceivedAtMs)
        scanner.stop()
    }

    @Test fun delayedCurrentSessionPacketKeepsItsTimeAndCannotRegressHealthFreshness() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        scanner.start { observations.add(it) }
        val callback = shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single()
        val device = adapter.getRemoteDevice("00:11:22:33:44:55")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        val earlierTime = SystemClock.elapsedRealtimeNanos()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))
        callback.onScanResult(1, ScanResult(device, null, -60, SystemClock.elapsedRealtimeNanos()))
        val latestTime = scanner.lastReceivedAtMs
        callback.onScanResult(1, ScanResult(device, null, -80, earlierTime))
        assertEquals(2, observations.size)
        assertTrue(System.currentTimeMillis() - observations.last().timestampMs in 20_000..20_500)
        assertEquals(latestTime, scanner.lastReceivedAtMs)
        scanner.stop()
    }

    @Test fun turningBluetoothOffStopsQuietlySoTheScanCanResumeWhenItIsBackOn() {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = BleScanner(context)
        assertTrue(scanner.start { })
        val result = ScanResult(adapter.getRemoteDevice("00:11:22:33:44:55"), null, -60, SystemClock.elapsedRealtimeNanos())
        shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single().onScanResult(1, result)

        // 감시 중에 블루투스가 꺼진 채로 다음 탐색 재시작 시점(4분)을 넘긴다.
        shadowOf(adapter).setEnabled(false)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))
        assertFalse(scanner.active)
        assertNull("Bluetooth off is not a scan error, so the view model may restart it", scanner.lastError)

        // 다시 켜면 새로 시작할 수 있다.
        shadowOf(adapter).setEnabled(true)
        assertTrue(scanner.isUsable())
        assertTrue(scanner.start { })
        scanner.stop()
    }
}
