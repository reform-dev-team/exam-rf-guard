package com.classroom.rfdetector.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import com.classroom.rfdetector.core.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BluetoothDiscoveryScannerTest {
    private val app = RuntimeEnvironment.getApplication()
    private val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var result: DiscoveryRound? = null

    @Before fun enable() { shadowOf(adapter).setEnabled(true) }
    @After fun stop() { scope.cancel() }
    private fun send(action: String) { app.sendBroadcast(Intent(action)); shadowOf(Looper.getMainLooper()).idle() }
    private fun start(): Job = scope.launch { result = BluetoothDiscoveryScanner(app).scan() }.also { shadowOf(Looper.getMainLooper()).idle() }
    private fun found(name: String? = null, rssi: Short? = null) {
        app.sendBroadcast(Intent(BluetoothDevice.ACTION_FOUND).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, adapter.getRemoteDevice("00:11:22:33:44:55"))
            if (name != null) putExtra(BluetoothDevice.EXTRA_NAME, name)
            if (rssi != null) putExtra(BluetoothDevice.EXTRA_RSSI, rssi)
        })
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun freshDiscoveryCollectsOptionalFieldsAndDeduplicatesAddresses() {
        start()
        found("Old queued device")
        send(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        found("Accessory", (-60).toShort())
        found()
        adapter.cancelDiscovery()
        send(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        assertEquals(DiscoveryStatus.COMPLETE, result!!.status)
        assertEquals("Accessory", result!!.peers.single().name)
        assertEquals(-60, result!!.peers.single().rssi)
        assertFalse(adapter.isDiscovering)
    }

    @Test fun timeoutKeepsPartialResultsAndCancelsOwnedDiscovery() {
        start()
        send(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        found()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DISCOVERY_WINDOW_MS))
        assertEquals(DiscoveryStatus.TIMED_OUT, result!!.status)
        assertNull(result!!.peers.single().rssi)
        assertFalse(adapter.isDiscovering)
    }

    @Test fun cancellationRemovesReceiverAndLateEventsCannotCompleteRound() {
        val job = start()
        send(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        job.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        found("Late")
        send(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        assertNull(result)
        assertFalse(adapter.isDiscovering)
    }

    @Test fun existingDiscoveryIsNotCancelledOrClaimed() {
        adapter.startDiscovery()
        start()
        assertEquals(DiscoveryStatus.BUSY, result!!.status)
        assertTrue(adapter.isDiscovering)
        adapter.cancelDiscovery()
    }

    @Test fun disabledBluetoothIsAFailureInsteadOfAnEmptySuccessfulScan() {
        shadowOf(adapter).setEnabled(false)
        start()
        assertEquals(DiscoveryStatus.FAILED, result!!.status)
    }
}
