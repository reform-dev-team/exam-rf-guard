package com.classroom.rfdetector.scanner

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.classroom.rfdetector.core.BleAddressType
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.bleDetails
import com.classroom.rfdetector.core.bleHints
import com.classroom.rfdetector.core.ScanSource

internal fun reportedAddressType(device: BluetoothDevice): BleAddressType {
    if (Build.VERSION.SDK_INT < 35) return BleAddressType.UNKNOWN
    return when (device.addressType) {
        BluetoothDevice.ADDRESS_TYPE_PUBLIC -> BleAddressType.PUBLIC
        BluetoothDevice.ADDRESS_TYPE_RANDOM -> BleAddressType.RANDOM
        else -> BleAddressType.UNKNOWN
    }
}

/** BLE 광고 연속 스캔. LOW_LATENCY 로 사실상 연속 수신한다 */
class BleScanner(context: Context) {
    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private companion object {
        /** 실제 기기에서 확인: 필터 없는 탐색은 시작 5분 뒤 시스템이 결과를 거의 주지 않는 모드로 바꾼다. 그 전에 다시 건다. */
        const val REFRESH_MS = 4 * 60_000L
        const val CHECK_MS = 30_000L
        /** 받던 신호가 이만큼 끊기면 시스템이 탐색을 낮춘 것으로 보고 바로 다시 건다. */
        const val SILENT_MS = 30_000L
    }
    private var scanStartedAtElapsedMs = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = Runnable { checkScan() }
    private var callback: ScanCallback? = null
    @Volatile var active: Boolean = false
        private set
    @Volatile var lastReceivedAtMs: Long? = null
        private set
    @Volatile var lastError: String? = null
        private set

    fun isUsable(): Boolean = try { adapter?.isEnabled == true } catch (_: SecurityException) { false }

    fun start(onObs: (RfObservation) -> Unit): Boolean {
        stop()
        lastReceivedAtMs = null
        lastError = null
        val ad = adapter ?: return fail("블루투스 기능이 없는 기기입니다")
        if (!isUsable()) return fail("블루투스가 꺼져 있거나 접근 권한이 없습니다")
        val scanner = try { ad.bluetoothLeScanner } catch (_: SecurityException) { null }
            ?: return fail("BLE 탐색을 열지 못했습니다 · 블루투스와 권한을 확인하세요")
        val startedAtElapsedNanos = SystemClock.elapsedRealtimeNanos()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (callback !== this || !active) return
                val observedElapsedNanos = result.timestampNanos
                val receivedElapsedNanos = SystemClock.elapsedRealtimeNanos()
                // Android defines this as observation time since boot, with no unknown/zero sentinel.
                // Do not assign an older room's packet or an unverifiable time to this session.
                if (observedElapsedNanos <= 0 || observedElapsedNanos < startedAtElapsedNanos || observedElapsedNanos > receivedElapsedNanos) return
                val name = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                val now = System.currentTimeMillis()
                val observedAt = now - (receivedElapsedNanos - observedElapsedNanos) / 1_000_000
                lastReceivedAtMs = maxOf(lastReceivedAtMs ?: observedAt, observedAt)
                lastError = null
                onObs(
                    RfObservation(
                        key = result.device.address,
                        source = ScanSource.BLE,
                        name = name,
                        rssi = result.rssi,
                        // AD Tx Power is radiated packet power, not calibrated RSSI at 1m.
                        txPower = null,
                        hidden = false,
                        timestampMs = observedAt,
                        manufacturerIds = result.scanRecord?.manufacturerSpecificData?.let { data ->
                            (0 until data.size()).map { data.keyAt(it) }.toSet()
                        } ?: emptySet(),
                        hints = bleHints(result.scanRecord?.bytes),
                        details = bleDetails(result.scanRecord?.bytes, result.isConnectable, result.isLegacy,
                            result.scanRecord?.txPowerLevel?.takeIf { it != Int.MIN_VALUE }),
                        bleAddressType = reportedAddressType(result.device),
                    )
                )
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                if (callback !== this) return
                handler.removeCallbacks(refresh)
                active = false
                fail("BLE 탐색 오류 ($errorCode) · 잠시 후 다시 시작하세요")
            }
        }
        return try {
            callback = cb
            active = true
            scanner.startScan(null, scanSettings(), cb)
            scanStartedAtElapsedMs = SystemClock.elapsedRealtime()
            if (active) handler.postDelayed(refresh, CHECK_MS)
            active
        } catch (e: SecurityException) {
            callback = null
            fail("BLE 탐색 권한을 확인하세요")
        } catch (_: IllegalStateException) {
            callback = null
            fail("블루투스 상태가 바뀌었습니다 · 다시 시작하세요")
        }
    }

    /** 확장 광고(BLE 5)와 지원하는 모든 PHY까지 받는다. 기본값은 레거시 광고만 전달한다. */
    private fun scanSettings() = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setReportDelay(0L)
        .setLegacy(false)
        .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
        .build()

    /** Android는 오래 이어진 탐색을 결과가 거의 오지 않는 모드로 낮춘다. 같은 콜백으로 다시 걸어 되돌린다. */
    private fun checkScan() {
        if (callback == null || !active) return
        val silentMs = lastReceivedAtMs?.let { System.currentTimeMillis() - it }
        val due = SystemClock.elapsedRealtime() - scanStartedAtElapsedMs >= REFRESH_MS
        if (due || (silentMs != null && silentMs > SILENT_MS)) refreshScan() else handler.postDelayed(refresh, CHECK_MS)
    }

    private fun refreshScan() {
        val cb = callback ?: return
        if (!active) return
        try {
            val scanner = adapter?.bluetoothLeScanner ?: run { fail("블루투스 상태가 바뀌었습니다 · 다시 시작하세요"); return }
            scanner.stopScan(cb)
            scanner.startScan(null, scanSettings(), cb)
            scanStartedAtElapsedMs = SystemClock.elapsedRealtime()
            handler.postDelayed(refresh, CHECK_MS)
        } catch (_: SecurityException) {
            fail("BLE 탐색 권한을 확인하세요")
        } catch (_: IllegalStateException) {
            fail("블루투스 상태가 바뀌었습니다 · 다시 시작하세요")
        }
    }

    fun stop() {
        handler.removeCallbacks(refresh)
        active = false
        val cb = callback ?: return
        callback = null
        val ad = adapter ?: return
        try {
            ad.bluetoothLeScanner?.stopScan(cb)
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
    }

    private fun fail(message: String): Boolean {
        active = false
        lastError = message
        return false
    }
}
