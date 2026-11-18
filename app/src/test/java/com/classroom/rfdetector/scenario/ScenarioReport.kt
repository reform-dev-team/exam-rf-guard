package com.classroom.rfdetector.scenario

import com.classroom.rfdetector.core.SignalHint
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 수신 간격·세기·빈 교실 조건을 바꿔 가며 돌린 결과를 표로 남긴다. 평소 테스트에서는 건너뛴다.
 *
 * ```
 * SCENARIO_REPORT=1 ./gradlew testDebugUnitTest --tests '*ScenarioReport*' --rerun
 * ```
 *
 * 결과는 app/build/reports/scenarios/sweep.md에 생긴다. 값은 모두 가정이며 실기기 결과가 아니다.
 */
class ScenarioReport {
    @Test fun sweep() {
        assumeTrue("SCENARIO_REPORT=1일 때만 만든다", System.getenv("SCENARIO_REPORT") == "1")
        val proctor = ScenarioDevice("감독관 기기", manufacturerIds = setOf(76), hints = setOf(SignalHint.APPLE_ACTIVE),
            rssi = constant(-50.0), rotateAddressEveryMs = 13 * 60_000L)
        val unrelated = ScenarioDevice("다른 종류 기기", manufacturerIds = setOf(6), rssi = constant(-50.0), rotateAddressEveryMs = 13 * 60_000L)

        val lines = mutableListOf(
            "# 시나리오 표",
            "",
            "가정한 값으로 돌린 결과입니다. 실기기 결과가 아닙니다. 시험 10분, 감시 화면 1초마다 계산, 주소는 13분마다 바뀜.",
            "",
        )
        for ((title, emptyRoom) in listOf("빈 교실에 같은 종류(Apple) 1대" to proctor, "빈 교실에 다른 종류만" to unrelated)) {
            lines += "## $title"
            lines += ""
            lines += MARKDOWN_HEADER
            for (every in listOf(1_000L, 14_000L, 16_000L, 40_000L)) for (dbm in listOf(-55.0, -92.0)) for (loss in listOf(0.0, 0.3)) {
                val label = "Apple · ${every / 1000}초마다 · ${dbm.toInt()} dBm · 손실 ${(loss * 100).toInt()}%"
                val device = ScenarioDevice(label, manufacturerIds = setOf(76), hints = setOf(SignalHint.APPLE_ACTIVE),
                    receiveEveryMs = every, lossRate = loss, rssi = constant(dbm), rotateAddressEveryMs = 13 * 60_000L)
                lines += ScenarioRunner.run(Scenario(listOf(emptyRoom), listOf(device))).markdownRows()
            }
            lines += ""
        }
        val out = File("build/reports/scenarios/sweep.md")
        out.parentFile.mkdirs()
        out.writeText(lines.joinToString("\n"))
        println("시나리오 표: ${out.absolutePath}")
    }
}
