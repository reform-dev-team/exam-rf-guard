package com.classroom.rfdetector.ui

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedRecordTimeTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(year, month, day, hour, minute).atZone(seoul).toInstant().toEpochMilli()
    private val now = at(2026, 11, 19, 8, 5)

    @Test fun recordFromTodayShowsOnlyTheTime() {
        assertEquals("07:40", savedRecordTime(at(2026, 11, 19, 7, 40), now, seoul))
        assertTrue(savedRecordIsFromToday(at(2026, 11, 19, 0, 0), now, seoul))
    }

    @Test fun olderRecordShowsItsDateSoItIsNotMistakenForTodays() {
        // 어제 같은 시각에 저장한 기록은 시각만 보면 오늘 것과 구분되지 않는다.
        assertEquals("어제 07:40", savedRecordTime(at(2026, 11, 18, 7, 40), now, seoul))
        assertEquals("어제 23:59", savedRecordTime(at(2026, 11, 18, 23, 59), now, seoul))
        assertEquals("11월 12일 07:40", savedRecordTime(at(2026, 11, 12, 7, 40), now, seoul))
        assertEquals("2025년 11월 13일 07:40", savedRecordTime(at(2025, 11, 13, 7, 40), now, seoul))
        assertFalse(savedRecordIsFromToday(at(2026, 11, 18, 23, 59), now, seoul))
    }

    @Test fun dayBoundaryFollowsThePhonesTimeZone() {
        // 같은 순간이 서울에서는 오늘 새벽, UTC에서는 전날 오후다.
        val saved = at(2026, 11, 19, 0, 30)
        val utc = ZoneId.of("UTC")
        assertEquals("00:30", savedRecordTime(saved, now, seoul))
        assertEquals("15:30", savedRecordTime(saved, now, utc))
        // 서울 09:30은 UTC로 날짜가 막 바뀐 뒤다. 서울에서는 여전히 오늘, UTC에서는 어제가 된다.
        val later = at(2026, 11, 19, 9, 30)
        assertTrue(savedRecordIsFromToday(saved, later, seoul))
        assertEquals("어제 15:30", savedRecordTime(saved, later, utc))
        assertFalse(savedRecordIsFromToday(saved, later, utc))
    }
}
