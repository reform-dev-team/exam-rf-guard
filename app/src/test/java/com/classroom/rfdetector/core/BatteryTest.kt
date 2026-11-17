package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class BatteryTest {
    @Test fun chargingOrUnknownIsQuiet() {
        assertEquals(BatteryWarning.NONE, batteryWarning(null))
        assertEquals(BatteryWarning.NONE, batteryWarning(BatteryState(5, charging = true)))
    }

    @Test fun unpluggedWarnsAndLowBatteryWarnsHarder() {
        assertEquals(BatteryWarning.NOT_CHARGING, batteryWarning(BatteryState(80, charging = false)))
        assertEquals(BatteryWarning.NOT_CHARGING, batteryWarning(BatteryState(LOW_BATTERY_PERCENT + 1, charging = false)))
        assertEquals(BatteryWarning.LOW, batteryWarning(BatteryState(LOW_BATTERY_PERCENT, charging = false)))
        assertEquals(BatteryWarning.NOT_CHARGING, batteryWarning(BatteryState(null, charging = false)))
    }

    @Test fun stateComesFromPlugAndScaledLevel() {
        assertEquals(BatteryState(50, charging = false), batteryState(level = 100, scale = 200, plugged = 0))
        assertEquals(BatteryState(100, charging = true), batteryState(level = 100, scale = 100, plugged = 2))
        assertEquals(BatteryState(null, charging = true), batteryState(level = -1, scale = 100, plugged = 1))
        assertNull(batteryState(level = 50, scale = 100, plugged = -1))
    }
}
