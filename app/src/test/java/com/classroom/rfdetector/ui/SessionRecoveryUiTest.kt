package com.classroom.rfdetector.ui

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.net.wifi.WifiManager
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionRecoveryUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val directory = TemporaryFolder()
    private var vm: AppViewModel? = null

    private fun launch(): AppViewModel {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions("android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.NEARBY_WIFI_DEVICES")
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setState(BluetoothAdapter.STATE_ON)
        (app.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled = true
        Settings.Secure.putInt(app.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
        shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).setNotificationPolicyAccessGranted(true)
        val now = System.currentTimeMillis()
        SessionRecoveryStore(directory.root).save(SessionRecovery(ScanSession("session", now - 60_000, "시험실"),
            SignalSnapshot("시험실", now - 50_000, false, emptyList()), setOf("BLE:name:earbuds"), now - 10_000))
        val model = AppViewModel(object : ContextWrapper(app) { override fun getFilesDir() = directory.root })
        vm = model
        compose.setContent {
            AppRfTheme { GuardScreen(model, false, model::startScan, {}, {}, {}) }
        }
        compose.waitUntil(10_000) { !model.recoveryLoading.value }
        return model
    }

    @After fun close() {
        vm?.let { model ->
            model.shutdown()
            val deadline = System.nanoTime() + 5_000_000_000L
            while (model.recoveryPendingWrites.value != 0 && System.nanoTime() < deadline) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
        }
    }

    @Test fun restoreRequiresExplicitSameExamConfirmationAndShowsUncertainGap() {
        val model = launch()
        compose.onNodeWithText("이전 감시 복원").assertExists().performClick()
        compose.onNodeWithText("같은 시험·같은 교실인가요?").assertExists()
        assertFalse(model.scanning.value)
        compose.onNodeWithText("취소").performClick()
        assertFalse(model.scanning.value)
        assertNotNull(model.pendingRecovery.value)
        compose.onNodeWithText("이전 감시 복원").performClick()
        compose.onNodeWithText("같은 시험·교실로 이어가기").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("종료된 감시를 복원했습니다"))
        compose.onNodeWithText("종료된 감시를 복원했습니다").assertExists()
        assertTrue(model.scanning.value)
        assertEquals(setOf("BLE:name:earbuds"), model.acknowledged.value)
    }

    @Test fun discardDoesNotStartMonitoring() {
        val model = launch()
        compose.onNodeWithText("이전 감시 버리기").performClick()
        compose.onNodeWithText("이전 감시 복원").assertDoesNotExist()
        assertFalse(model.scanning.value)
        assertNull(model.pendingRecovery.value)
    }
}
