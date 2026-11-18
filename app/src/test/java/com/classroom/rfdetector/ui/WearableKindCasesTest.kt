package com.classroom.rfdetector.ui

import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.SignalHint
import com.classroom.rfdetector.core.WearableCompanyCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 화면에 붙는 종류 이름 사례. 한 줄이 사례 하나이고, 줄을 더하면 그대로 시험된다.
 * name이 null이면 이름을 알리지 않은 기기다(앱이 "BLE 기기 끝자리"를 대신 붙인다).
 */
class WearableKindCasesTest {
    private data class Case(val what: String, val source: ScanSource, val name: String?, val ids: Set<Int>, val hints: Set<SignalHint>, val expected: String)

    private val candidate = WearableCompanyCatalog.LABEL

    private val cases = listOf(
        // 2단계: 이름 없이 회사 번호만 있는 신호
        Case("이름을 지운 레이밴 메타 — Luxottica 번호", ScanSource.BLE, null, setOf(0x0D53), emptySet(), candidate),
        Case("이름을 지운 Meta 기기 — 안경이거나 Quest 헤드셋", ScanSource.BLE, null, setOf(0x01AB), emptySet(), candidate),
        Case("Meta Platforms Technologies 번호", ScanSource.BLE, null, setOf(0x058E), emptySet(), candidate),
        Case("이름 없는 Snap Spectacles", ScanSource.BLE, null, setOf(0x03C2), emptySet(), candidate),
        Case("이름 없는 Jieli 칩 기기 — 값싼 이어폰이거나 카메라 안경", ScanSource.BLE, null, setOf(0x05D6), emptySet(), "무선 이어폰·스마트 안경 후보"),
        Case("이름 없는 Valve 기기 — 헤드셋 후보", ScanSource.BLE, null, setOf(0x055D), emptySet(), candidate),
        Case("Apple 번호와 Luxottica 번호를 같이 실은 신호", ScanSource.BLE, null, setOf(0x004C, 0x0D53), emptySet(), candidate),
        // 1단계: 이름이 그대로면 이름 규칙이 먼저다
        Case("이름을 그대로 둔 레이밴 메타", ScanSource.BLE, "Ray-Ban Meta", setOf(0x0D53), emptySet(), "스마트 안경"),
        Case("이름만 있고 회사 번호 없는 오클리 메타", ScanSource.BLE, "Oakley Meta HSTN", emptySet(), emptySet(), "스마트 안경"),
        // 기기가 스스로 종류를 알리면 그 종류가 먼저다
        Case("이어폰이라고 알린 Jieli 칩 이어폰", ScanSource.BLE, null, setOf(0x05D6), setOf(SignalHint.FAST_PAIR), SignalHint.FAST_PAIR.label),
        Case("표준 종류 번호가 안경인 신호", ScanSource.BLE, null, emptySet(), setOf(SignalHint.GLASSES), "스마트 안경"),
        // 목록 밖은 바뀌지 않는다
        Case("목록에 없는 회사 번호", ScanSource.BLE, null, setOf(0x0D54), emptySet(), "블루투스 기기 · 종류 모름"),
        Case("회사 번호가 없는 신호", ScanSource.BLE, null, emptySet(), emptySet(), "블루투스 기기 · 종류 모름"),
        Case("켜져 있는 아이폰", ScanSource.BLE, null, setOf(0x004C), setOf(SignalHint.APPLE_ACTIVE), SignalHint.APPLE_ACTIVE.label),
        Case("이름 없는 삼성 기기 — 휴대폰일 수 있어 안경 후보로 붙이지 않음", ScanSource.BLE, null, setOf(0x0075), emptySet(), "블루투스 기기 · 종류 모름"),
        Case("와이파이는 회사 번호를 보지 않음", ScanSource.WIFI, "Ray-Ban Meta", emptySet(), emptySet(), "와이파이 · 공유기나 휴대폰 핫스팟"),
    )

    @Test fun everyCaseShowsTheExpectedKind() {
        val address = "AA:BB:CC:DD:EE:01"
        val wrong = cases.mapNotNull { case ->
            val name = case.name ?: "${if (case.source == ScanSource.WIFI) "Wi-Fi" else "BLE"} 기기 ${address.takeLast(5)}"
            val shown = plainKind(case.source, address, name, case.hints, case.ids).first
            if (shown == case.expected) null else "${case.what}: 기대 \"${case.expected}\", 실제 \"$shown\""
        }
        assertEquals(emptyList<String>(), wrong)
    }
}
