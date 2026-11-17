package com.classroom.rfdetector.scenario

import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.SignalHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 시뮬레이터가 앱의 판정을 그대로 따르는지, 그리고 이슈로 알려진 지금 동작을 고정한다.
 * "지금 동작" 테스트는 해당 이슈가 고쳐지면 바뀌어야 한다. 그때 기대값을 고친다.
 */
class ScenarioSimulatorTest {
    private val proctorWatch = ScenarioDevice(
        label = "감독관 기기", manufacturerIds = setOf(76), hints = setOf(SignalHint.APPLE_ACTIVE),
        rssi = constant(-50.0), rotateAddressEveryMs = 13 * 60_000L,
    )

    private fun apple(label: String, everyMs: Long, dbm: Double, jitter: Double = 0.1) = ScenarioDevice(
        label = label, manufacturerIds = setOf(76), hints = setOf(SignalHint.APPLE_ACTIVE),
        receiveEveryMs = everyMs, jitter = jitter, rssi = constant(dbm), rotateAddressEveryMs = 13 * 60_000L,
    )

    @Test fun sameRunWithTheSameSeedGivesTheSameResult() {
        val scenario = Scenario(listOf(proctorWatch), listOf(apple("새 기기", 16_000, -55.0).copy(lossRate = 0.2)))
        assertEquals(ScenarioRunner.run(scenario).devices, ScenarioRunner.run(scenario).devices)
    }

    @Test fun emptyRoomDeviceThatRotatesItsAddressIsNeverFlagged() {
        // 주소를 두 번 바꾸는 25분 동안 원래 있던 기기를 새 전파로 보이지 않는다.
        val result = ScenarioRunner.run(Scenario(listOf(proctorWatch), emptyList(), examMs = 25 * 60_000L))
        assertEquals(mapOf("BLE:kind:76:APPLE_ACTIVE" to 1), result.baseline)
        assertEquals(0, result.falseNewTicks)
    }

    @Test fun kindAbsentFromTheEmptyRoomShowsAtOnce() {
        val buds = ScenarioDevice("이어폰", advertisedName = "Galaxy Buds2 Pro", manufacturerIds = setOf(117), rssi = constant(-60.0), fromMs = 60_000)
        val result = ScenarioRunner.run(Scenario(listOf(proctorWatch), listOf(buds))).device("이어폰")
        assertTrue(result.firstNewAfterMs!! <= 2_000)
        assertTrue(result.newRatio > 0.95)
    }

    @Test fun anotherDeviceOfTheSameKindShowsAfterThirtySeconds() {
        val result = ScenarioRunner.run(Scenario(listOf(proctorWatch), listOf(apple("새 기기", 1_000, -55.0)))).device("새 기기")
        assertTrue(result.firstNewAfterMs!! in 30_000..33_000)
        assertTrue(result.newRatio > 0.9)
    }

    @Test fun newWifiNameShowsAtOnce() {
        val router = ScenarioDevice("학교 공유기", ScanSource.WIFI, advertisedName = "SCHOOL", receiveEveryMs = 30_000, rssi = constant(-55.0))
        val hotspot = ScenarioDevice("핫스팟", ScanSource.WIFI, advertisedName = "Galaxy S24 1234", receiveEveryMs = 30_000, rssi = constant(-60.0), fromMs = 60_000)
        val result = ScenarioRunner.run(Scenario(listOf(router), listOf(hotspot)))
        assertEquals(0, result.falseNewTicks)
        assertTrue(result.device("핫스팟").firstNewAfterMs!! <= 31_000)
    }

    /** 지금 동작(#28): 빈 교실에 같은 종류가 있으면, 15초보다 띄엄띄엄 잡히는 새 기기는 계속 잡혀도 세지 않는다. */
    @Test fun currentBehaviorIssue28_sameKindReceivedEvery16SecondsIsNeverCounted() {
        // 한 묶음에 함께 들면 서로의 결과에 섞이므로 따로 돌린다.
        fun alone(device: ScenarioDevice) = ScenarioRunner.run(Scenario(listOf(proctorWatch), listOf(device))).device(device.label)
        assertNotNull(alone(apple("14초마다", 14_000, -55.0, jitter = 0.0)).firstNewAfterMs)
        val sparse = alone(apple("16초마다", 16_000, -55.0, jitter = 0.0))
        assertNull(sparse.firstNewAfterMs)
        assertEquals(0, sparse.newTicks)
    }

    /** 같은 16초 간격이라도 빈 교실에 없던 종류면 바로 보인다. #28이 같은 종류일 때만 생긴다는 확인. */
    @Test fun issue28DoesNotAffectKindsAbsentFromTheEmptyRoom() {
        val sparse = ScenarioDevice("16초마다", manufacturerIds = setOf(117), receiveEveryMs = 16_000, jitter = 0.0, rssi = constant(-55.0), fromMs = 10_000)
        val result = ScenarioRunner.run(Scenario(listOf(proctorWatch), listOf(sparse))).device("16초마다")
        assertTrue(result.firstNewAfterMs!! <= 17_000)
    }

    /** 지금 동작(#19): 세게 잡힌 새 기기가 멀어져 약해지면 새 전파에서 빠지고 "멀리서 약하게"로 옮겨 간다. */
    @Test fun currentBehaviorIssue19_newDeviceThatWeakensLeavesTheNewList() {
        val leaving = ScenarioDevice("멀어지는 이어폰", advertisedName = "Galaxy Buds2 Pro", manufacturerIds = setOf(117),
            rssi = ramp(-55.0, -95.0, startMs = 120_000 + 120_000, overMs = 60_000), rssiNoiseDb = 1.0)
        val result = ScenarioRunner.run(Scenario(listOf(proctorWatch), listOf(leaving), examMs = 300_000)).device("멀어지는 이어폰")
        assertTrue(result.newTicks in 120..200)
        assertTrue(result.farTicks > 60)
    }
}
