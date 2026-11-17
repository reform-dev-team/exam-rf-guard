package com.classroom.rfdetector.inspection.radio

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Looper
import android.os.SystemClock
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.radioKey
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TargetRadioReceiverTest {
    private val address = "00:11:22:33:44:55"
    @Test fun gateRequiredAndLostGateStopsOnlyTargetObserver() {
        val app = RuntimeEnvironment.getApplication()
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = adapter.bluetoothLeScanner
        val main = object : android.bluetooth.le.ScanCallback() {}
        scanner.startScan(main)
        val receiver = TargetRadioReceiver(app)
        val model = RoomSurvey(radioKey(ScanSource.BLE, address), ScanSource.BLE)
        model.accept(model.trackingKey, 1L, -60, 1L)
        assertTrue(model.setReference(1L))
        var allowed = false
        var stopped = false
        receiver.start(ScanSource.BLE, address, { allowed }, { _, _, _ -> fail("unexpected packet") }, {})
        assertFalse(receiver.active)
        assertEquals(1, shadowOf(scanner).scanCallbacks.size)
        allowed = true
        receiver.start(ScanSource.BLE, address, { allowed }, { _, _, _ -> fail("unexpected packet") }, {
            stopped = !receiver.active
            if (stopped) model.stop()
        })
        val callback = shadowOf(scanner).scanCallbacks.first { it !== main }
        allowed = false
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        callback.onScanResult(1, ScanResult(adapter.getRemoteDevice(address), null, -60, SystemClock.elapsedRealtimeNanos()))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(stopped)
        assertFalse(receiver.active)
        assertEquals(Approach.LOST, model.approach(SystemClock.elapsedRealtimeNanos()))
        assertTrue(shadowOf(scanner).scanCallbacks.contains(main))
        scanner.stopScan(main)
    }

    @Test fun bluetoothOldFutureAndStoppedCallbacksCannotBecomeNewPointSamples() {
        val app = RuntimeEnvironment.getApplication()
        val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        val receiver = TargetRadioReceiver(app)
        val model = RoomSurvey(radioKey(ScanSource.BLE,address),ScanSource.BLE)
        receiver.start(ScanSource.BLE,address,{ true },{ key,time,rssi -> model.accept(key,time,rssi,SystemClock.elapsedRealtimeNanos()) },{})
        assertTrue(receiver.active)
        val callback = shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single()
        val began = SystemClock.elapsedRealtimeNanos()
        model.begin(Checkpoint.FRONT_LEFT,began)
        val device = adapter.getRemoteDevice(address)
        callback.onScanResult(1,ScanResult(device,null,-60,began-1))
        callback.onScanResult(1,ScanResult(device,null,-60,began+1_000_000_000))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0,model.sampleCount)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        callback.onScanResult(1,ScanResult(device,null,-60,SystemClock.elapsedRealtimeNanos()))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,model.sampleCount)
        receiver.stop()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        callback.onScanResult(1,ScanResult(device,null,-40,SystemClock.elapsedRealtimeNanos()))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,model.sampleCount)
        assertFalse(receiver.active)
    }

    @Test fun wifiBroadcastDoesNotRelabelCachedPacketAtNewCheckpoint() {
        val app = RuntimeEnvironment.getApplication()
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.isWifiEnabled = true
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        val receiver = TargetRadioReceiver(app)
        val model = RoomSurvey(radioKey(ScanSource.WIFI,address),ScanSource.WIFI)
        receiver.start(ScanSource.WIFI,address,{ true },{ key,time,rssi -> model.accept(key,time,rssi,SystemClock.elapsedRealtimeNanos()) },{})
        assertTrue(receiver.active)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        val oldTime = SystemClock.elapsedRealtimeNanos()/1000
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        model.begin(Checkpoint.FRONT_LEFT,SystemClock.elapsedRealtimeNanos())
        fun broadcast(time: Long) {
            val result = android.net.wifi.ScanResult().apply { BSSID=address; level=-60; timestamp=time }
            shadowOf(wifi).setScanResults(listOf(result))
            app.sendBroadcast(Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION).putExtra(WifiManager.EXTRA_RESULTS_UPDATED,true))
            shadowOf(Looper.getMainLooper()).idle()
        }
        broadcast(oldTime)
        assertEquals(0,model.sampleCount)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        val fresh = SystemClock.elapsedRealtimeNanos()/1000
        broadcast(fresh)
        broadcast(fresh)
        assertEquals(1,model.sampleCount)
        assertTrue(model.finish(SystemClock.elapsedRealtimeNanos()))
        receiver.stop()
        model.begin(Checkpoint.FRONT_RIGHT,SystemClock.elapsedRealtimeNanos())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
        broadcast(SystemClock.elapsedRealtimeNanos()/1000)
        assertEquals(0,model.sampleCount)
    }
}
