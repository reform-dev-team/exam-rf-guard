package com.classroom.rfdetector.core

/**
 * Full UI-model refresh cadence. Scanner callbacks still record observations immediately.
 *
 * Collection only needs a once-per-second clock and count update. Monitoring keeps the
 * signal list responsive at 4 Hz, while Geiger feedback retains the existing 10 Hz cadence.
 */
internal object RefreshCadence {
    const val COLLECTION_INTERVAL_MS = 1_000L
    const val MONITORING_INTERVAL_MS = 250L
    const val GEIGER_INTERVAL_MS = 100L

    fun intervalMs(baselineSet: Boolean, geigerOn: Boolean): Long = when {
        geigerOn -> GEIGER_INTERVAL_MS
        baselineSet -> MONITORING_INTERVAL_MS
        else -> COLLECTION_INTERVAL_MS
    }
}
