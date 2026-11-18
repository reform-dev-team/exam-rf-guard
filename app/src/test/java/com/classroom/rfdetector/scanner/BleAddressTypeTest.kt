package com.classroom.rfdetector.scanner

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.SystemClock
import com.classroom.rfdetector.core.BleAddressType
import com.classroom.rfdetector.core.RfObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class BleAddressTypeTest {
    @Before fun clockHasStarted() { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }

    private fun observe(addressType: Int): RfObservation {
        val context = RuntimeEnvironment.getApplication()
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        shadowOf(adapter).setEnabled(true)
        val scanner = BleScanner(context)
        val observations = mutableListOf<RfObservation>()
        assertTrue(scanner.start { observations.add(it) })
        val device = adapter.getRemoteLeDevice("58:AD:12:34:56:78", addressType)
        shadowOf(adapter.bluetoothLeScanner).scanCallbacks.single().onScanResult(1, ScanResult(device, null, -60, SystemClock.elapsedRealtimeNanos()))
        scanner.stop()
        return observations.single()
    }

    @Config(sdk = [35])
    @Test fun android15ReportsWhetherTheAddressIsPublicOrRandom() {
        assertEquals(BleAddressType.RANDOM, observe(BluetoothDevice.ADDRESS_TYPE_RANDOM).bleAddressType)
        assertEquals(BleAddressType.PUBLIC, observe(BluetoothDevice.ADDRESS_TYPE_PUBLIC).bleAddressType)
    }

    @Config(sdk = [34])
    @Test fun android14AndEarlierCannotTellTheAddressType() {
        assertEquals(BleAddressType.UNKNOWN, observe(BluetoothDevice.ADDRESS_TYPE_RANDOM).bleAddressType)
        assertEquals(BleAddressType.UNKNOWN, observe(BluetoothDevice.ADDRESS_TYPE_PUBLIC).bleAddressType)
    }
}
