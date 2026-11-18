package com.classroom.rfdetector.inspection.flow

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.classroom.rfdetector.App
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.SignalSnapshot
import com.classroom.rfdetector.ui.AppRfTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomWalkIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Suppress("DEPRECATION")
    @Test fun surveyUsesMonitorScannerAndFindDoesNotAdoptAnotherAddress() {
        val app = RuntimeEnvironment.getApplication()
        val adapter = app.getSystemService(BluetoothManager::class.java).adapter
        val selected = "02:00:00:00:00:01"
        val other = "02:00:00:00:00:02"
        shadowOf(adapter).setEnabled(true)
        app.getSystemService(WifiManager::class.java).setWifiEnabled(true)
        shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        shadowOf(app.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(true)
        shadowOf(app.getSystemService(SensorManager::class.java)).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
        compose.setContent { AppRfTheme { App() } }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        try {
            compose.runOnIdle {
                shadowOf(app).grantPermissions(*vm.requiredPermissions().toTypedArray())
                vm.baselineSnapshot.value = SignalSnapshot("시험실", System.currentTimeMillis(), false, emptyList())
                vm.startScan()
            }
            val scanner = shadowOf(adapter.bluetoothLeScanner)
            val callback = scanner.scanCallbacks.single()
            fun tick(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
            fun receive(address: String, strength: Int, ageSeconds: Long = 0) {
                val timestamp = SystemClock.elapsedRealtimeNanos() - Duration.ofSeconds(ageSeconds).toNanos()
                callback.onScanResult(1, ScanResult(adapter.getRemoteDevice(address), null, strength, timestamp))
            }
            compose.onNodeWithText("교실 둘러보기 · 블루투스").performScrollTo().performClick()
            fun walkAction(label: String) {
                compose.onNodeWithTag("room-walk").performScrollToNode(hasText(label))
                compose.onNodeWithText(label).performClick()
            }
            walkAction("교실 측정 시작")
            repeat(5) { point ->
                walkAction("이 지점에서 측정")
                compose.runOnIdle {
                    repeat(2) {
                        tick(1)
                        receive(selected, if (point == 0) -48 else -75)
                    }
                    tick(7) // The hold ends at 8 s; allow the monitor's next refresh.
                }
            }
            assertEquals(listOf(WalkCandidate("BLE:$selected", WalkZone("앞쪽 왼쪽", 0f, 1f))), vm.walkUi.value.result)
            assertEquals(callback, scanner.scanCallbacks.single())
            // Drive the scanner's age conversion explicitly; Robolectric looper time need not advance JVM wall time.
            compose.runOnIdle { vm.tracker.clear(); receive(selected, -75, ageSeconds = 7); tick(1) }
            compose.onNodeWithTag("walk-result-map").assertIsDisplayed()
            compose.onNodeWithContentDescription("$selected 신호 찾기").performTouchInput { click() }
            compose.onNodeWithText("현재 수신 없음").assertExists()
            compose.runOnIdle { receive(other, -40); tick(1) }
            compose.onNodeWithText("현재 수신 없음").assertExists()
            assertNull(vm.findTarget.value)
            compose.runOnIdle { vm.tracker.clear(); receive(selected, -96); receive(other, -40); tick(1) }
            compose.onNodeWithText("신호가 약하거나 세기를 알 수 없음").assertExists()
            compose.runOnIdle { receive(selected, -45); tick(1) }
            compose.onNodeWithText("현재 수신 없음").assertDoesNotExist()
            compose.onNode(isToggleable()).performScrollTo().performClick()
            assertEquals("BLE:$selected", vm.findTarget.value)
            compose.runOnIdle { vm.tracker.clear(); receive(selected, -75, ageSeconds = 7); receive(other, -40); tick(1) }
            compose.onNodeWithText("현재 수신 없음").assertExists()
            assertEquals("BLE:$selected", vm.findTarget.value)
            compose.onNodeWithText("둘러보기 결과로").performClick()
            assertNull(vm.findTarget.value)
            assertTrue(vm.scanning.value)
        } finally {
            compose.runOnIdle { vm.shutdown() }
        }
    }
}
