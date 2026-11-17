package com.classroom.rfdetector.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanSessionTimeTest {
    @Test fun timeAwayFromTheAppIsNotCountedAsReceiving() {
        // 측정을 시작하고 5초 뒤 화면을 꺼 3분 동안 두었다가 돌아온 경우: 실제로 받은 시간은 5초뿐이다.
        val session = ScanSession("s", startedAtMs = 0, area = "시험실", pauses = 1, pausedMs = 180_000)
        assertEquals(5_000L, session.receivingMs(185_000))
    }

    @Test fun noPauseMeansReceivingSinceStart() {
        assertEquals(120_000L, ScanSession("s", startedAtMs = 1_000, area = "시험실").receivingMs(121_000))
    }

    @Test fun neverNegativeBeforeTheFirstRefreshAfterReturning() {
        // 돌아온 직후 첫 갱신 전에는 화면의 시각이 멈추기 전 값이라 빼면 음수가 된다.
        val session = ScanSession("s", startedAtMs = 0, area = "시험실", pauses = 1, pausedMs = 180_000)
        assertEquals(0L, session.receivingMs(5_000))
    }
}
