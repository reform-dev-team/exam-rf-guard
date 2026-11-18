package com.classroom.rfdetector.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearableCompanyCatalogTest {
    private val registry = File("src/main/assets/bluetooth-companies.tsv").readLines()
        .filter { it.contains('\t') }.associate { it.substringBefore('\t').toInt() to it.substringAfter('\t') }

    private fun hex(id: Int) = "0x" + id.toString(16).uppercase().padStart(4, '0')

    /** 다른 앱 목록에서 옮긴 번호가 실제로는 다른 회사 것인 경우가 있었다. 예: 0x0BA7은 Pico가 아니라 hearX(청각 기기) 번호다. */
    @Test fun everyNumberBelongsToTheNamedCompanyInTheOfficialRegistry() {
        WearableCompanyCatalog.entries.forEach { entry ->
            assertEquals("${hex(entry.id)}의 회사 이름이 공식 등록부와 다릅니다", registry[entry.id], entry.registryName)
        }
    }

    @Test fun eachNumberIsListedOnce() {
        val duplicated = WearableCompanyCatalog.entries.groupBy { it.id }.filterValues { it.size > 1 }.keys.map(::hex)
        assertEquals(emptyList<String>(), duplicated)
    }

    /**
     * 휴대폰·이어폰·노트북이 같은 번호를 쓰는 회사는 넣지 않는다. 숨긴 휴대폰이 "안경 후보"로 뜨면
     * 감독관이 엉뚱한 곳을 찾는다. 이 회사들의 신호도 빈 교실 기록에 없으면 새 전파로 그대로 알린다.
     */
    @Test fun companiesWhosePhonesShareTheNumberAreLeftOut() {
        val phoneMakers = mapOf(0x004C to "Apple", 0x0075 to "Samsung", 0x00E0 to "Google", 0x018E to "Google LLC", 0x0006 to "Microsoft", 0x012D to "Sony")
        phoneMakers.forEach { (id, name) -> assertNull("$name(${hex(id)})는 목록에 넣지 않습니다", WearableCompanyCatalog.match(setOf(id))) }
    }

    @Test fun aListedNumberIsFoundAmongOtherNumbers() {
        assertEquals("Luxottica Group S.p.A", WearableCompanyCatalog.match(setOf(0x004C, 0x0D53))!!.registryName)
        assertNull(WearableCompanyCatalog.match(setOf(0x0D54)))
        assertNull(WearableCompanyCatalog.match(emptySet()))
    }

    @Test fun everyEntryNamesWhatItLooksFor() {
        WearableCompanyCatalog.entries.forEach { assertTrue("${hex(it.id)}: 찾으려는 제품이 비어 있습니다", it.products.isNotBlank()) }
    }

    @Test fun everyTitleSaysCandidate() {
        WearableCompanyCatalog.entries.forEach { assertTrue("${hex(it.id)}: 제목 \"${it.label}\"이 \"후보\"로 끝나지 않습니다", it.label.endsWith("후보")) }
    }

    /**
     * 목록은 화면에 붙이는 이름만 바꾸고 전파를 묶고 세는 기준은 바꾸지 않는다.
     * 목록에 있는 번호마다, 목록에 없는 번호(0x0D54)와 똑같이 묶이고 똑같이 세는지 본다.
     */
    @Test fun listedNumbersAreGroupedAndCountedExactlyLikeAnUnlistedNumber() {
        fun countsFor(id: Int): List<Int> {
            val tracker = DeviceTracker()
            fun seen(address: String, atMs: Long) = tracker.update(RfObservation(address, ScanSource.BLE, null, -60, null, false, atMs, setOf(id)))
            seen("AA:00:00:00:00:01", 0)
            val baseline = baselineGroupCounts(SignalSnapshot.capture("A", 1_000, false, tracker.snapshot(1_000, includeInactive = true)))
            fun count(nowMs: Long) = watchView(tracker.snapshot(nowMs, includeInactive = true), baseline, emptySet(), nowMs).count
            // 빈 교실 때 있던 기기가 다시 잡힘 → 새 전파 아님
            val sameDevice = run { seen("AA:00:00:00:00:01", 60_000); count(61_000) }
            // 같은 회사 번호의 기기가 하나 더 들어와 30초 넘게 이어짐 → 새 전파 1개
            val oneMore = run {
                (70_000L..100_000L step 10_000).forEach { seen("AA:00:00:00:00:01", it); seen("AA:00:00:00:00:02", it) }
                count(101_000)
            }
            // 빈 교실 기록에 없던 회사 번호 → 새 전파 1개
            val emptyRoomCount = watchView(tracker.snapshot(101_000, includeInactive = true), emptyMap(), emptySet(), 101_000).count
            return listOf(baseline.values.sum(), sameDevice, oneMore, emptyRoomCount)
        }
        val unlisted = countsFor(0x0D54)
        assertEquals(listOf(1, 0, 1, 1), unlisted)
        WearableCompanyCatalog.entries.forEach { entry ->
            assertEquals("${hex(entry.id)}: 묶는 이름이 바뀌었습니다", "BLE:kind:${entry.id}:", signalGroupKey(ScanSource.BLE, "AA:00:00:00:00:01", "BLE 기기 00:01", setOf(entry.id), emptySet()))
            assertEquals("${hex(entry.id)}: 세는 개수가 목록에 없는 번호와 다릅니다", unlisted, countsFor(entry.id))
        }
    }
}
