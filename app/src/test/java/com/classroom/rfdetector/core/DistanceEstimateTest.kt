package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class DistanceEstimateTest {
    @Test fun invalidStrengthDoesNotInventDistance() {
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 0.0, -200.0).forEach { assertNull(estimateDistance(it)) }
    }

    @Test fun weakerSignalHasLargerModelDistanceAndOneMeterReferenceIsHonored() {
        assertEquals(1.0, estimateDistance(-59.0, -59.0)!!.meters, 0.0001)
        assertTrue(estimateDistance(-80.0)!!.meters > estimateDistance(-60.0)!!.meters)
        assertEquals(1.0, estimateDistance(-70.0, -70.0)!!.meters, 0.0001)
    }

    @Test fun wifiUsesItsOwnReferenceSoInRoomHotspotsAreNotAllRightNextToYou() {
        fun wifiClass(rssi: Int): DistanceClass {
            val tracker = DeviceTracker()
            tracker.update(RfObservation("02:11:22:33:44:55", ScanSource.WIFI, "AndroidAP_1234", rssi, null, false, 0))
            return tracker.snapshot(0).single().distanceClass
        }
        // #37: 블루투스 기준값으로는 -35 ~ -64 dBm 와이파이가 모두 "바로 옆"이었다.
        assertEquals(DistanceClass.VERY_CLOSE, wifiClass(-35))
        assertEquals(DistanceClass.CLOSE, wifiClass(-50))
        assertEquals(DistanceClass.NEAR, wifiClass(-60))
        assertEquals(DistanceClass.FAR, wifiClass(-70))
        assertEquals(1.0, estimateDistance(-40.0, source = ScanSource.WIFI)!!.meters, 0.0001)
    }

    @Test fun bluetoothDistanceIsUnchanged() {
        val tracker = DeviceTracker()
        tracker.update(RfObservation("AA:BB:CC:DD:EE:FF", ScanSource.BLE, "earbud", -59, null, false, 0))
        assertEquals(DistanceClass.VERY_CLOSE, tracker.snapshot(0).single().distanceClass)
        assertEquals(1.0, estimateDistance(-59.0, source = ScanSource.BLE)!!.meters, 0.0001)
        assertEquals(estimateDistance(-70.0)!!.meters, estimateDistance(-70.0, source = ScanSource.BLE)!!.meters, 0.0)
    }
}
