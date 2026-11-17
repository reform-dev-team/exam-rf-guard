package com.classroom.rfdetector.scanner

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.classroom.rfdetector.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

class BluetoothDiscoveryScanner(context: Context) : DiscoveryScanner {
    override val source = ScanSource.BLUETOOTH_DISCOVERY
    private val app = context.applicationContext
    private val adapter = (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    override suspend fun scan(): DiscoveryRound {
        val peers = linkedMapOf<String, DiscoveryPeer>()
        fun result(status: DiscoveryStatus, message: String? = null) =
            DiscoveryRound(status, peers.values.toList(), System.currentTimeMillis(), message)
        val radio = adapter ?: return result(DiscoveryStatus.UNSUPPORTED, "Bluetooth 기능이 없는 기기입니다")
        var owned = false
        var registered = false
        var receiver: BroadcastReceiver? = null
        try {
            if (!radio.isEnabled) return result(DiscoveryStatus.FAILED, "Bluetooth가 꺼져 있습니다")
            if (radio.isDiscovering) return result(DiscoveryStatus.BUSY, "다른 Bluetooth 탐색이 진행 중입니다")
            return withTimeoutOrNull(DISCOVERY_WINDOW_MS) {
                suspendCancellableCoroutine { continuation ->
                    var started = false
                    val listener = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (!continuation.isActive) return
                            try {
                                when (intent.action) {
                                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> started = true
                                    BluetoothDevice.ACTION_FOUND -> {
                                        if (!started) return
                                        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                                        val address = device.address
                                        val old = peers[address]
                                        peers[address] = DiscoveryPeer(address,
                                            intent.getStringExtra(BluetoothDevice.EXTRA_NAME)?.takeIf { it.isNotBlank() } ?: old?.name,
                                            if (intent.hasExtra(BluetoothDevice.EXTRA_RSSI)) intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt().takeIf { it in -127..20 } else old?.rssi,
                                            when (device.type) {
                                                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic 지원"
                                                BluetoothDevice.DEVICE_TYPE_DUAL -> "Classic·BLE 지원"
                                                BluetoothDevice.DEVICE_TYPE_LE -> "BLE 지원"
                                                else -> null
                                            })
                                    }
                                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (started) {
                                        owned = false
                                        continuation.resumeWith(Result.success(result(DiscoveryStatus.COMPLETE)))
                                    }
                                    BluetoothAdapter.ACTION_STATE_CHANGED -> if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) != BluetoothAdapter.STATE_ON) {
                                        continuation.resumeWith(Result.success(result(DiscoveryStatus.FAILED, "Bluetooth 상태가 바뀌었습니다")))
                                    }
                                }
                            } catch (_: SecurityException) {
                                continuation.resumeWith(Result.success(result(DiscoveryStatus.FAILED, "Bluetooth 탐색 권한을 확인하세요")))
                            }
                        }
                    }
                    receiver = listener
                    ContextCompat.registerReceiver(app, listener, IntentFilter().apply {
                        addAction(BluetoothDevice.ACTION_FOUND)
                        addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    }, ContextCompat.RECEIVER_EXPORTED)
                    registered = true
                    owned = radio.startDiscovery()
                    if (!owned && continuation.isActive) continuation.resumeWith(Result.success(result(DiscoveryStatus.FAILED, "Bluetooth 탐색을 시작하지 못했습니다")))
                }
            } ?: result(DiscoveryStatus.TIMED_OUT, "30초 안에 탐색이 끝나지 않았습니다 · 부분 결과")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            return result(DiscoveryStatus.FAILED, "Bluetooth 탐색 권한을 확인하세요")
        } catch (_: IllegalStateException) {
            return result(DiscoveryStatus.FAILED, "Bluetooth 상태가 바뀌었습니다")
        } finally {
            if (registered) receiver?.let { app.unregisterReceiver(it) }
            // Discovery belongs to the system service; activity lifecycle alone does not stop it.
            if (owned) try { radio.cancelDiscovery() } catch (_: SecurityException) { }
        }
    }
}
