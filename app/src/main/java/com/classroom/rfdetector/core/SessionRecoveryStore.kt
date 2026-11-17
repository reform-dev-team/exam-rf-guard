package com.classroom.rfdetector.core

import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Only a checkpoint of decisions, never a persisted live receiver/device list. */
data class SessionRecovery(
    val session: ScanSession,
    val baseline: SignalSnapshot,
    val acknowledged: Set<String>,
    val savedAtMs: Long,
    val interruptedAtMs: Long? = null,
) {
    // A conservative retention limit, not proof that the room or exam is the same.
    fun canRestore(nowMs: Long): Boolean = !baseline.simulation &&
        session.startedAtMs in 1..savedAtMs && baseline.capturedAtMs in 1..savedAtMs &&
        savedAtMs <= nowMs && nowMs - savedAtMs <= MAX_AGE_MS &&
        (interruptedAtMs == null || interruptedAtMs in session.startedAtMs..savedAtMs)

    fun restoredSession(nowMs: Long): ScanSession = session.copy(
        recoveries = session.recoveries + 1,
        uncertainMs = session.uncertainMs + (nowMs - (interruptedAtMs ?: savedAtMs)).coerceAtLeast(0),
    )

    companion object { const val MAX_AGE_MS = 24 * 60 * 60_000L }
}

/** Atomic tombstones prevent an earlier checkpoint from returning after a normal stop. */
class SessionRecoveryStore(directory: File) {
    private val file = AtomicFile(File(directory, "session-recovery.json"))
    private val snapshots = SnapshotStore(directory)

    fun load(): SessionRecovery? {
        val text = try { file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() } }
            catch (_: java.io.FileNotFoundException) { return null }
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "Unsupported session recovery version" }
        if (root.isNull("session")) return null
        val session = root.getJSONObject("session")
        val keys = root.getJSONArray("acknowledged")
        return SessionRecovery(
            ScanSession(session.getString("id"), session.getLong("startedAtMs"), session.getString("area"),
                session.getInt("pauses"), session.getLong("pausedMs"),
                session.getInt("recoveries"), session.getLong("uncertainMs")).also {
                require(it.id.isNotBlank() && it.pauses >= 0 && it.pausedMs >= 0 && it.recoveries >= 0 && it.uncertainMs >= 0)
            },
            snapshots.decode(root.getJSONObject("baseline")),
            (0 until keys.length()).map { keys.getString(it) }.toSet(),
            root.getLong("savedAtMs"),
            if (root.isNull("interruptedAtMs")) null else root.getLong("interruptedAtMs"),
        )
    }

    fun save(recovery: SessionRecovery?) {
        val root = JSONObject().put("version", 1)
        if (recovery == null) root.put("session", JSONObject.NULL)
        else {
            val session = recovery.session
            root.put("session", JSONObject().put("id", session.id).put("startedAtMs", session.startedAtMs)
                .put("area", session.area).put("pauses", session.pauses).put("pausedMs", session.pausedMs)
                .put("recoveries", session.recoveries).put("uncertainMs", session.uncertainMs))
            root.put("baseline", snapshots.encode(recovery.baseline))
            root.put("acknowledged", JSONArray(recovery.acknowledged.sorted()))
            root.put("savedAtMs", recovery.savedAtMs)
            root.put("interruptedAtMs", recovery.interruptedAtMs ?: JSONObject.NULL)
        }
        val stream = file.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }
}
