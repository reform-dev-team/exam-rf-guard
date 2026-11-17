package com.classroom.rfdetector.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWifiP2pManager
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], shadows = [DiscoveryP2pShadow::class, DiscoveryP2pChannelShadow::class])
class WifiP2pScannerTest {
    private val app = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val service = Shadow.extract<DiscoveryP2pShadow>(manager)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var result: DiscoveryRound? = null

    @Before fun support() { shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_WIFI_DIRECT, true) }
    @After fun stop() { scope.cancel() }
    private fun start(): Job = scope.launch { result = WifiP2pScanner(app).scan() }.also { shadowOf(Looper.getMainLooper()).idle() }
    private fun peersChanged() {
        app.sendBroadcast(Intent(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun peer(address: String, status: Int = WifiP2pDevice.AVAILABLE) = WifiP2pDevice().apply {
        deviceAddress = address; deviceName = "P2P accessory"; this.status = status
    }

    @Test fun finalListReplacesEarlierPeersWithoutInventingRssiOrAccumulatingLostDevices() {
        start()
        service.peers = listOf(peer("00:11:22:33:44:01"))
        peersChanged()
        assertNull(result)
        service.peers = listOf(peer("00:11:22:33:44:02"), peer("00:11:22:33:44:03", WifiP2pDevice.UNAVAILABLE))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(25))
        assertEquals(DiscoveryStatus.COMPLETE, result!!.status)
        assertEquals("00:11:22:33:44:02", result!!.peers.single().address)
        assertNull(result!!.peers.single().rssi)
        assertEquals(1, service.stopCalls)
        assertTrue(Shadow.extract<DiscoveryP2pChannelShadow>(service.channel).closed)
    }

    @Test fun successfulEmptyListIsACompletedRound() {
        start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(25))
        assertEquals(DiscoveryStatus.COMPLETE, result!!.status)
        assertTrue(result!!.peers.isEmpty())
    }

    @Test fun finalSnapshotCannotBeStarvedByPeerChangeEventsOrOverwrittenByAnOlderCallback() {
        service.holdPeers = true
        start()
        service.peers = listOf(peer("00:11:22:33:44:01"))
        peersChanged()
        service.peers = listOf(peer("00:11:22:33:44:02"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(25))
        peersChanged()
        assertEquals(2, service.pendingCount)
        service.deliverPending(reverse = true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DiscoveryStatus.COMPLETE, result!!.status)
        assertEquals("00:11:22:33:44:02", result!!.peers.single().address)
    }

    @Test fun startSuccessAloneCannotCompleteRoundAndMissingFinalCallbackTimesOut() {
        service.holdPeers = true
        start()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(DiscoveryStatus.TIMED_OUT, result!!.status)
        service.deliverPending()
        assertEquals(DiscoveryStatus.TIMED_OUT, result!!.status)
        assertEquals(1, service.stopCalls)
    }

    @Test fun timeoutDoesNotMakeAnEarlierPartialListLookNewlyReceived() {
        start()
        service.peers = listOf(peer("00:11:22:33:44:01"))
        val beforeReport = System.currentTimeMillis()
        peersChanged()
        val afterReport = System.currentTimeMillis()
        service.holdPeers = true
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(DiscoveryStatus.TIMED_OUT, result!!.status)
        assertTrue(result!!.reportedAtMs in beforeReport..afterReport)
        assertEquals(1, result!!.peers.size)
    }

    @Test fun cancellationCleansUpAndIgnoresPendingCallbacks() {
        service.holdPeers = true
        val job = start()
        peersChanged()
        job.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        service.deliverPending()
        assertNull(result)
        assertEquals(1, service.stopCalls)
        assertTrue(Shadow.extract<DiscoveryP2pChannelShadow>(service.channel).closed)
    }

    @Test fun existingDiscoveryIsLeftAlone() {
        service.discoveryState = WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
        start()
        assertEquals(DiscoveryStatus.BUSY, result!!.status)
        assertEquals(0, service.startCalls)
        assertEquals(0, service.stopCalls)
    }

    @Test fun busyStartAndUnsupportedHardwareDoNotBecomeEmptySuccesses() {
        service.startFailure = WifiP2pManager.BUSY
        start()
        assertEquals(DiscoveryStatus.BUSY, result!!.status)
        assertEquals(0, service.stopCalls)
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_WIFI_DIRECT, false)
        start()
        assertEquals(DiscoveryStatus.UNSUPPORTED, result!!.status)
    }

    @Test fun asynchronousPermissionFailureAndChannelLossAreReportedWithoutCrashing() {
        service.denyDiscovery = true
        start()
        assertEquals(DiscoveryStatus.FAILED, result!!.status)
        service.denyDiscovery = false
        start()
        service.channelListener?.onChannelDisconnected()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(DiscoveryStatus.FAILED, result!!.status)
        assertTrue(result!!.message!!.contains("연결이 끊겼습니다"))
    }
}

/** Robolectric's bundled P2P shadow does not implement peer discovery or list callbacks. */
@Implements(WifiP2pManager::class)
class DiscoveryP2pShadow : ShadowWifiP2pManager() {
    var discoveryState = WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED
    var peers: List<WifiP2pDevice> = emptyList()
    var holdPeers = false
    var denyDiscovery = false
    var startFailure: Int? = null
    var startCalls = 0
    var stopCalls = 0
    lateinit var channel: WifiP2pManager.Channel
    var channelListener: WifiP2pManager.ChannelListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<() -> Unit>()

    @Implementation
    override fun initialize(context: Context, looper: Looper, listener: WifiP2pManager.ChannelListener?): WifiP2pManager.Channel {
        channelListener = listener
        channel = super.initialize(context, looper, listener)
        return channel
    }

    @Implementation
    fun requestDiscoveryState(channel: WifiP2pManager.Channel, listener: WifiP2pManager.DiscoveryStateListener) {
        handler.post { listener.onDiscoveryStateAvailable(discoveryState) }
    }

    @Implementation
    fun discoverPeers(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener) {
        if (denyDiscovery) throw SecurityException("Permission revoked")
        startCalls++
        handler.post { startFailure?.let { listener.onFailure(it) } ?: listener.onSuccess() }
    }

    @Implementation
    fun stopPeerDiscovery(channel: WifiP2pManager.Channel, listener: WifiP2pManager.ActionListener?) {
        stopCalls++
        listener?.onSuccess()
    }

    @Implementation
    fun requestPeers(channel: WifiP2pManager.Channel, listener: WifiP2pManager.PeerListListener) {
        val list = WifiP2pDeviceList()
        peers.forEach { ReflectionHelpers.callInstanceMethod<Void>(list, "update", ClassParameter.from(WifiP2pDevice::class.java, it)) }
        val response = { listener.onPeersAvailable(list) }
        if (holdPeers) pending += response else handler.post { response() }
    }

    val pendingCount: Int get() = pending.size
    fun deliverPending(reverse: Boolean = false) {
        (if (reverse) pending.toList().asReversed() else pending.toList()).forEach { it() }
        pending.clear()
    }
}

@Implements(WifiP2pManager.Channel::class)
class DiscoveryP2pChannelShadow {
    var closed = false
    @Implementation fun close() { closed = true }
}
