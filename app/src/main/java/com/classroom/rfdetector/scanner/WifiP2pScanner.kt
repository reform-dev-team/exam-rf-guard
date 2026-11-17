package com.classroom.rfdetector.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.classroom.rfdetector.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** A reported peer list has no packet capture timestamps or RSSI. It is never fed to continuous ranging. */
class WifiP2pScanner(context: Context) : DiscoveryScanner {
    override val source = ScanSource.WIFI_P2P
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    override suspend fun scan(): DiscoveryRound {
        var peers = emptyList<DiscoveryPeer>()
        var reportedAtMs: Long? = null
        fun result(status: DiscoveryStatus, message: String? = null) =
            DiscoveryRound(status, peers, reportedAtMs ?: System.currentTimeMillis(), message)
        val service = manager?.takeIf { app.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) }
            ?: return result(DiscoveryStatus.UNSUPPORTED, "Wi-Fi Direct를 지원하지 않는 기기입니다")
        val handler = Handler(Looper.getMainLooper())
        var channel: WifiP2pManager.Channel? = null
        var requested = false
        var owned = false
        var registered = false
        var receiver: BroadcastReceiver? = null
        try {
            return withTimeoutOrNull(DISCOVERY_WINDOW_MS) {
                suspendCancellableCoroutine { continuation ->
                    fun finish(status: DiscoveryStatus, message: String? = null) {
                        if (continuation.isActive) continuation.resumeWith(Result.success(result(status, message)))
                    }
                    fun protect(action: () -> Unit) {
                        if (!continuation.isActive) return
                        try { action() }
                        catch (_: SecurityException) { finish(DiscoveryStatus.FAILED, "Wi-Fi Direct 권한과 위치 설정을 확인하세요") }
                        catch (_: IllegalStateException) { finish(DiscoveryStatus.FAILED, "Wi-Fi Direct 상태가 바뀌었습니다") }
                    }
                    val activeChannel = service.initialize(app, Looper.getMainLooper()) {
                        finish(DiscoveryStatus.FAILED, "Wi-Fi Direct 연결이 끊겼습니다")
                    }
                    channel = activeChannel
                    if (activeChannel == null) {
                        finish(DiscoveryStatus.FAILED, "Wi-Fi Direct 탐색을 열지 못했습니다")
                        return@suspendCancellableCoroutine
                    }
                    var requestedList = 0
                    var acceptedList = 0
                    var finalRequested = false
                    fun readPeers(final: Boolean) = protect {
                        if (!final && finalRequested) return@protect
                        if (final) finalRequested = true
                        val sequence = ++requestedList
                        service.requestPeers(activeChannel) { list ->
                            if (continuation.isActive && owned && sequence >= acceptedList) {
                                acceptedList = sequence
                                reportedAtMs = System.currentTimeMillis()
                                peers = list.deviceList.filter { it.status != WifiP2pDevice.UNAVAILABLE && it.status != WifiP2pDevice.FAILED }.mapNotNull { peer ->
                                    val address = peer.deviceAddress?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                    DiscoveryPeer(address, peer.deviceName?.takeIf { it.isNotBlank() }, kind = "P2P 목록 보고")
                                }
                                if (final) finish(DiscoveryStatus.COMPLETE)
                            }
                        }
                    }
                    val listener = object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            if (!continuation.isActive) return
                            when (intent.action) {
                                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> if (owned) readPeers(false)
                                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1) == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                                    finish(DiscoveryStatus.FAILED, "Wi-Fi Direct가 꺼졌습니다")
                                }
                                WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> if (owned && intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1) == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                                    finish(DiscoveryStatus.FAILED, "Wi-Fi Direct 탐색이 중단됐습니다")
                                }
                            }
                        }
                    }
                    receiver = listener
                    ContextCompat.registerReceiver(app, listener, IntentFilter().apply {
                        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                        addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
                    }, ContextCompat.RECEIVER_EXPORTED)
                    registered = true
                    service.requestDiscoveryState(activeChannel) { state -> protect {
                        if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                            finish(DiscoveryStatus.BUSY, "다른 Wi-Fi Direct 탐색이 진행 중입니다")
                        } else {
                            requested = true
                            service.discoverPeers(activeChannel, object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    if (!continuation.isActive) return
                                    owned = true
                                    // Reserve time inside the 30-second deadline for the final asynchronous list response.
                                    handler.postDelayed({ readPeers(true) }, DISCOVERY_WINDOW_MS - 5_000)
                                }
                                override fun onFailure(reason: Int) {
                                    requested = false
                                    finish(when (reason) {
                                        WifiP2pManager.BUSY -> DiscoveryStatus.BUSY
                                        WifiP2pManager.P2P_UNSUPPORTED -> DiscoveryStatus.UNSUPPORTED
                                        else -> DiscoveryStatus.FAILED
                                    }, "Wi-Fi Direct 탐색을 시작하지 못했습니다 ($reason)")
                                }
                            })
                        }
                    } }
                }
            } ?: result(DiscoveryStatus.TIMED_OUT, "30초 안에 목록을 받지 못했습니다 · 이전 또는 부분 결과")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            return result(DiscoveryStatus.FAILED, "Wi-Fi Direct 권한과 위치 설정을 확인하세요")
        } catch (_: IllegalStateException) {
            return result(DiscoveryStatus.FAILED, "Wi-Fi Direct 상태가 바뀌었습니다")
        } finally {
            owned = false
            handler.removeCallbacksAndMessages(null)
            if (registered) receiver?.let { app.unregisterReceiver(it) }
            channel?.let { activeChannel ->
                if (requested) try { service.stopPeerDiscovery(activeChannel, null) } catch (_: SecurityException) { } catch (_: IllegalStateException) { }
                activeChannel.close()
            }
        }
    }
}
