package com.classroom.rfdetector

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import com.classroom.rfdetector.core.SignalSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomRotationTest {
    @Test fun activeHoldSurvivesLandscapeAndPortrait() {
        val app = RuntimeEnvironment.getApplication()
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        shadowOf(adapter).setEnabled(true)
        app.getSystemService(WifiManager::class.java).setWifiEnabled(true)
        shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(true)
        shadowOf(app.getSystemService(SensorManager::class.java)).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        val ctl = Robolectric.buildActivity(MainActivity::class.java).setup()
        fun tick(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        tick(100)
        val vm = ViewModelProvider(ctl.get())[AppViewModel::class.java]
        shadowOf(app).grantPermissions(*vm.requiredPermissions().toTypedArray())
        vm.baselineSnapshot.value = SignalSnapshot("교실", System.currentTimeMillis(), false, emptyList())
        vm.startScan(); vm.startRoomWalk(); vm.markRoomWalk(); tick(1200)
        assertTrue(vm.walkUi.value.active)
        val scanner = shadowOf(adapter.bluetoothLeScanner)
        val callback = scanner.scanCallbacks.single()
        try {
            for (orientation in listOf(Configuration.ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_PORTRAIT)) {
                val conf = Configuration(ctl.get().resources.configuration)
                conf.orientation = orientation
                conf.screenWidthDp = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 915 else 412
                conf.screenHeightDp = if (orientation == Configuration.ORIENTATION_LANDSCAPE) 412 else 915
                ctl.configurationChange(conf); tick(200)
                assertTrue("Hold interrupted on rotation",vm.walkUi.value.active && vm.walkUi.value.holdSeconds != null)
                assertSame(callback,scanner.scanCallbacks.single())
            }
            tick(8000); assertEquals(1,vm.walkUi.value.pointIndex)
            ctl.pause().stop(); assertFalse(vm.scanning.value)
        } finally { vm.shutdown() }
    }
}
