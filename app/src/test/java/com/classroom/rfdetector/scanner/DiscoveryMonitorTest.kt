package com.classroom.rfdetector.scanner

import android.os.Looper
import android.os.SystemClock
import com.classroom.rfdetector.core.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryMonitorTest {
    @Test fun scansAreSequentialAndRepeatFromCycleStartWithoutChangingTheBaselineDuringWatch() {
        val calls = mutableListOf<Pair<ScanSource, Long>>()
        var collecting = true
        fun scanner(kind: ScanSource) = object : DiscoveryScanner {
            override val source = kind
            override suspend fun scan(): DiscoveryRound {
                calls += source to SystemClock.elapsedRealtime()
                val peers = if (calls.size >= 3) listOf(DiscoveryPeer("new", "New during exam")) else emptyList()
                delay(1000)
                return DiscoveryRound(DiscoveryStatus.COMPLETE, peers, System.currentTimeMillis())
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val monitor = DiscoveryMonitor(listOf(scanner(ScanSource.BLUETOOTH_DISCOVERY), scanner(ScanSource.WIFI_P2P))) { collecting }
        try {
            monitor.start(scope)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            assertEquals(listOf(ScanSource.BLUETOOTH_DISCOVERY, ScanSource.WIFI_P2P), calls.map { it.first })
            assertEquals(1000L, calls[1].second - calls[0].second)
            assertEquals(2, monitor.baselines.size)
            collecting = false
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(118))
            assertEquals(3, calls.size)
            assertEquals(DISCOVERY_PERIOD_MS, calls[2].second - calls[0].second)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(1, monitor.states.value.getValue(ScanSource.BLUETOOTH_DISCOVERY).peers.size)
            assertTrue(monitor.baselines.values.all { it.isEmpty() })
            assertTrue(monitor.baselineAddresses.values.all { it.isEmpty() })
            monitor.stop()
            assertTrue(monitor.states.value.values.none { it.usable })
            val before = calls.size
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))
            assertEquals(before, calls.size)
            monitor.start(scope)
            assertEquals(before + 1, calls.size)
        } finally { monitor.stop(); scope.cancel() }
    }

    @Test fun cancelledOldRoundCannotPublishAfterRestart() {
        var calls = 0
        val scanner = object : DiscoveryScanner {
            override val source = ScanSource.BLUETOOTH_DISCOVERY
            override suspend fun scan(): DiscoveryRound {
                val call = ++calls
                withContext(NonCancellable) { delay(if (call == 1) 10_000 else 1000) }
                return DiscoveryRound(DiscoveryStatus.COMPLETE, listOf(DiscoveryPeer("$call", "Round $call")), System.currentTimeMillis())
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val monitor = DiscoveryMonitor(listOf(scanner)) { true }
        try {
            monitor.start(scope)
            monitor.stop()
            monitor.start(scope)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
            assertEquals("2", monitor.states.value.getValue(scanner.source).peers.single().address)
        } finally { monitor.stop(); scope.cancel() }
    }
}
