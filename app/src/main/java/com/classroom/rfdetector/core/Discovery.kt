package com.classroom.rfdetector.core

import java.util.Locale

const val DISCOVERY_PERIOD_MS = 120_000L
const val DISCOVERY_WINDOW_MS = 30_000L

enum class DiscoveryStatus { IDLE, SCANNING, COMPLETE, FAILED, TIMED_OUT, BUSY, UNSUPPORTED, PAUSED }

data class DiscoveryPeer(val address: String, val name: String?, val rssi: Int? = null, val kind: String? = null)

data class DiscoveryRound(
    val status: DiscoveryStatus,
    val peers: List<DiscoveryPeer> = emptyList(),
    val reportedAtMs: Long,
    val message: String? = null,
)

data class DiscoveryState(
    val status: DiscoveryStatus = DiscoveryStatus.IDLE,
    val peers: List<DiscoveryPeer> = emptyList(),
    val reportedAtMs: Long? = null,
    val lastSuccessAtMs: Long? = null,
    val message: String? = null,
    val usable: Boolean = false,
) {
    fun isCurrent(nowMs: Long): Boolean = usable && lastSuccessAtMs?.let {
        nowMs - it in 0..(DISCOVERY_PERIOD_MS + 2 * DISCOVERY_WINDOW_MS)
    } == true
}

fun discoveryGroupKey(source: ScanSource, name: String?): String =
    name?.trim()?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)?.let { "${source.name}:name:$it" }
        ?: "${source.name}:unknown"

data class DiscoveryGroup(val key: String, val peers: List<DiscoveryPeer>, val baselineCount: Int?) {
    val increased: Boolean get() = baselineCount != null && peers.size > baselineCount
}

fun discoveryGroups(source: ScanSource, peers: List<DiscoveryPeer>, baseline: Map<String, Int>?): List<DiscoveryGroup> =
    peers.distinctBy { it.address.uppercase(Locale.ROOT) }.groupBy { discoveryGroupKey(source, it.name) }
        .map { (key, members) -> DiscoveryGroup(key, members, baseline?.let { it[key] ?: 0 }) }
        .sortedWith(compareByDescending<DiscoveryGroup> { it.increased }.thenBy { it.key })

/** Periodic lists are snapshots, not a stream of packet receptions. Never accumulate rotating addresses across rounds. */
class DiscoveryTracker(sources: List<ScanSource>) {
    private val initial = sources.associateWith { DiscoveryState() }
    var states: Map<ScanSource, DiscoveryState> = initial
        private set
    var baselines: Map<ScanSource, Map<String, Int>> = emptyMap()
        private set

    fun reset() { states = initial; baselines = emptyMap() }

    fun begin(source: ScanSource) {
        states = states + (source to states.getValue(source).copy(status = DiscoveryStatus.SCANNING, message = null))
    }

    fun finish(source: ScanSource, round: DiscoveryRound, collecting: Boolean) {
        val peers = round.peers.distinctBy { it.address.uppercase(Locale.ROOT) }
        val success = round.status == DiscoveryStatus.COMPLETE
        val previous = states.getValue(source)
        states = states + (source to DiscoveryState(round.status,
            if (success || peers.isNotEmpty()) peers else previous.peers,
            if (success || peers.isNotEmpty()) round.reportedAtMs else previous.reportedAtMs,
            if (success) round.reportedAtMs else previous.lastSuccessAtMs, round.message, success))
        if (success && collecting) {
            val counts = discoveryGroups(source, peers, null).associate { it.key to it.peers.size }
            val previousCounts = baselines[source].orEmpty()
            baselines = baselines + (source to (previousCounts.keys + counts.keys).associateWith {
                maxOf(previousCounts[it] ?: 0, counts[it] ?: 0)
            })
        }
    }

    fun pause() { states = states.mapValues { (_, state) -> state.copy(status = DiscoveryStatus.PAUSED, usable = false) } }
}
