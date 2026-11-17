package com.classroom.rfdetector

import android.os.Build
import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 31, 33, 35])
class RequiredPermissionsTest {
    @Test fun runtimePermissionsFollowPlatformBoundariesAndKeepLocationForScanning() {
        val vm = AppViewModel(RuntimeEnvironment.getApplication())
        try {
            val permissions = vm.requiredPermissions()
            assertTrue("android.permission.ACCESS_FINE_LOCATION" in permissions)
            assertEquals(Build.VERSION.SDK_INT >= 31, "android.permission.BLUETOOTH_SCAN" in permissions)
            assertEquals(Build.VERSION.SDK_INT >= 31, "android.permission.BLUETOOTH_CONNECT" in permissions)
            assertEquals(Build.VERSION.SDK_INT >= 33, "android.permission.NEARBY_WIFI_DEVICES" in permissions)
            val app = RuntimeEnvironment.getApplication()
            val manifestPermissions = app.packageManager.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            assertFalse("android.permission.INTERNET" in manifestPermissions)
        } finally { vm.shutdown() }
    }
}
