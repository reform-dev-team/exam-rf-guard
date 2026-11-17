package com.classroom.rfdetector.scanner

import android.os.SystemClock
import com.classroom.rfdetector.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface DiscoveryScanner {
    val source: ScanSource
    suspend fun scan(): DiscoveryRound
}

/** Run additional discovery sequentially without changing the continuous BLE/AP scanners. */
class DiscoveryMonitor(private val scanners: List<DiscoveryScanner>, private val collecting: () -> Boolean) {
    private val tracker = DiscoveryTracker(scanners.map { it.source })
    private val mutableStates = MutableStateFlow(tracker.states)
    val states: StateFlow<Map<ScanSource, DiscoveryState>> = mutableStates
    val baselines: Map<ScanSource, Map<String, Int>> get() = tracker.baselines
    val baselineAddresses: Map<ScanSource, Map<String, String>> get() = tracker.baselineAddresses
    private var job: Job? = null

    fun reset() {
        stop()
        tracker.reset()
        mutableStates.value = tracker.states
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val started = SystemClock.elapsedRealtime()
                for (scanner in scanners) {
                    ensureActive()
                    tracker.begin(scanner.source)
                    mutableStates.value = tracker.states
                    val result = try { scanner.scan() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { DiscoveryRound(DiscoveryStatus.FAILED, reportedAtMs = System.currentTimeMillis(), message = "추가 탐색을 완료하지 못했습니다") }
                    ensureActive()
                    tracker.finish(scanner.source, result, collecting())
                    mutableStates.value = tracker.states
                }
                delay((DISCOVERY_PERIOD_MS - (SystemClock.elapsedRealtime() - started)).coerceAtLeast(1))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        tracker.pause()
        mutableStates.value = tracker.states
    }
}
