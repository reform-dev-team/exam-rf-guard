package com.classroom.rfdetector.inspection.radio

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.ApplicationInfo
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.provider.Settings
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.ScanSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowBluetoothAdapter

/** Robolectric 4.14 always reports LE off in airplane mode, even after manual enable. */
@Implements(BluetoothAdapter::class)
class ExamBluetoothShadow : ShadowBluetoothAdapter() {
    @Implementation
    override fun getLeState(): Int = if (isEnabled()) BluetoothAdapter.STATE_ON else BluetoothAdapter.STATE_OFF
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ExamBluetoothShadow::class])
class SurveyPreflightTest {

    @Test fun requiresActiveMonitoringAndEveryExistingExamPrerequisite() {
        val app = RuntimeEnvironment.getApplication()
        val notification = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val location = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val bluetooth = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(bluetooth).setEnabled(true)
        val vm = AppViewModel(app)
        val applicationInfo = app.applicationInfo
        val originalFlags = applicationInfo.flags
        try {
            shadowOf(app).grantPermissions(*vm.requiredPermissions().toTypedArray())
            shadowOf(notification).setNotificationPolicyAccessGranted(true)
            wifi.isWifiEnabled = true
            shadowOf(location).setLocationEnabled(true)
            Settings.Global.putInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
            assertFalse(vm.surveyAllowed(ScanSource.BLE))
            vm.startScan()
            assertTrue("The prepared BLE scanner must be active", vm.surveyAllowed(ScanSource.BLE))
            assertTrue(vm.surveyAllowed(ScanSource.WIFI))

            Settings.Global.putInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
            applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_DEBUGGABLE
            assertTrue("Debuggable BLE follows the setup exemption", vm.surveyAllowed(ScanSource.BLE))
            assertTrue("Debuggable Wi-Fi follows the setup exemption", vm.surveyAllowed(ScanSource.WIFI))
            applicationInfo.flags = originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            assertFalse("Release BLE still requires airplane mode", vm.surveyAllowed(ScanSource.BLE))
            assertFalse("Release Wi-Fi still requires airplane mode", vm.surveyAllowed(ScanSource.WIFI))
            applicationInfo.flags = originalFlags
            Settings.Global.putInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 1)
            shadowOf(notification).setNotificationPolicyAccessGranted(false)
            assertFalse("DND access is still required", vm.surveyAllowed(ScanSource.BLE))
            shadowOf(notification).setNotificationPolicyAccessGranted(true)
            notification.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            assertFalse("Active exam silence is still required", vm.surveyAllowed(ScanSource.BLE))
            notification.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            assertFalse("Location permission is still required", vm.surveyAllowed(ScanSource.WIFI))
            shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
            shadowOf(location).setLocationEnabled(false)
            assertFalse("Location setting is still required", vm.surveyAllowed(ScanSource.WIFI))
            shadowOf(location).setLocationEnabled(true)
            wifi.isWifiEnabled = false
            assertFalse("Radio settings are still required", vm.surveyAllowed(ScanSource.BLE))
            wifi.isWifiEnabled = true
            assertTrue(vm.surveyAllowed(ScanSource.WIFI))
            vm.ble.stop()
            assertFalse("A stopped base scanner cannot be bypassed", vm.surveyAllowed(ScanSource.BLE))
            vm.stopScan()
            assertFalse(vm.surveyAllowed(ScanSource.WIFI))
        } finally { applicationInfo.flags = originalFlags; vm.shutdown() }
    }
}
