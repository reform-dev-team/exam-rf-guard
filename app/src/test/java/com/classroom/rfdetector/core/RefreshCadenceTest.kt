package com.classroom.rfdetector.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RefreshCadenceTest {
    @Test
    fun emptyRoomCollectionRefreshesOncePerSecond() {
        assertEquals(1_000L, RefreshCadence.intervalMs(baselineSet = false, geigerOn = false))
    }

    @Test
    fun ordinaryMonitoringRefreshesFourTimesPerSecond() {
        assertEquals(250L, RefreshCadence.intervalMs(baselineSet = true, geigerOn = false))
    }

    @Test
    fun geigerMonitoringKeepsTheExistingTenHzCadence() {
        assertEquals(100L, RefreshCadence.intervalMs(baselineSet = true, geigerOn = true))
    }

    @Test
    fun geigerCadenceTakesPrecedenceOverCollectionCadence() {
        assertEquals(100L, RefreshCadence.intervalMs(baselineSet = false, geigerOn = true))
    }
}
