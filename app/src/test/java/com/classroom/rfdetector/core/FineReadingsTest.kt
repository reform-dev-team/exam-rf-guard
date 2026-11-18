package com.classroom.rfdetector.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FineReadingsTest {
    @Test fun oneDbAndSilenceAndIdentity() {
        val t = DeviceTracker()
        fun sample(id: String, v: Int, time: Long) = t.update(RfObservation(id, ScanSource.BLE, null, v, null, false, time))
        fun values() = t.snapshot(7000).first { it.key == "A" }.fineReadings
        sample("A", -60, 1000); sample("B", -40, 1050); sample("A", -59, 1100); sample("A", -80, 1040)
        assertEquals(listOf(-60,-59), values())
        sample("A", -65, 6000); assertEquals(listOf(-65), values())
        sample("A", 127, 6050); assertEquals(emptyList<Int>(), values())
    }
}
