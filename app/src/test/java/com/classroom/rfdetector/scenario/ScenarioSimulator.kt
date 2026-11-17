package com.classroom.rfdetector.scenario

import com.classroom.rfdetector.core.DeviceTracker
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.SignalHint
import com.classroom.rfdetector.core.SignalSnapshot
import com.classroom.rfdetector.core.WatchView
import com.classroom.rfdetector.core.baselineGroupCounts
import com.classroom.rfdetector.core.baselineSamples
import com.classroom.rfdetector.core.baselineWifiAddresses
import com.classroom.rfdetector.core.watchView
import java.util.Locale
import java.util.Random
import kotlin.math.roundToInt

/**
 * 가상 기기가 정해진 간격으로 잡히는 상황을 만들어, 앱과 같은 순서(DeviceTracker → 빈 교실 기록 → watchView)로 돌린다.
 *
 * 여기 넣는 값(수신 간격, 손실률, 세기)은 모두 가정이다. 이 도구는 "기기가 이렇게 잡힌다면 앱이 어떻게 보이는가"를
 * 답할 뿐, 실제 기기가 그렇게 잡히는지는 답하지 않는다. 실기기에서 잰 값을 넣으면 그 조건에서의 결과를 볼 수 있다.
 */
data class ScenarioDevice(
    /** 결과 표에 쓰는 이름. 앱에 보내는 기기 이름은 [advertisedName]이다. */
    val label: String,
    val source: ScanSource = ScanSource.BLE,
    val advertisedName: String? = null,
    val manufacturerIds: Set<Int> = emptySet(),
    val hints: Set<SignalHint> = emptySet(),
    /** 앱이 이 기기를 받는 평균 간격. 기기의 광고 간격이 아니라 휴대폰이 실제로 받는 간격이다. */
    val receiveEveryMs: Long = 1_000,
    /** 간격을 이 비율만큼 무작위로 흔든다. 0.2면 ±20%. */
    val jitter: Double = 0.1,
    /** 받을 차례에 받지 못할 확률. 가방 속, 먼 자리, 사람 몸에 가리는 경우를 흉내 낸다. */
    val lossRate: Double = 0.0,
    /** 시각(시나리오 시작 기준 ms) → 평균 세기(dBm). */
    val rssi: (Long) -> Double = constant(-60.0),
    /** 받을 때마다 세기에 더하는 잡음의 표준편차(dB). */
    val rssiNoiseDb: Double = 3.0,
    /** 이 간격마다 주소를 바꾼다. null이면 바꾸지 않는다(와이파이). */
    val rotateAddressEveryMs: Long? = null,
    /** 시나리오 시작 기준으로 켜지는 시각과 꺼지는 시각. */
    val fromMs: Long = 0,
    val untilMs: Long = Long.MAX_VALUE,
)

fun constant(dbm: Double): (Long) -> Double = { dbm }

/** [startMs]부터 [overMs]에 걸쳐 [fromDbm]에서 [toDbm]으로 바뀌고, 그 뒤로는 [toDbm]을 유지한다. */
fun ramp(fromDbm: Double, toDbm: Double, startMs: Long, overMs: Long): (Long) -> Double = { t ->
    val f = ((t - startMs).toDouble() / overMs).coerceIn(0.0, 1.0)
    fromDbm + (toDbm - fromDbm) * f
}

data class Scenario(
    /** 빈 교실 기록 때부터 있던 기기. 시험 중에도 계속 잡힌다. */
    val emptyRoom: List<ScenarioDevice>,
    /** 시험 중에 들어오는 기기. fromMs/untilMs는 시험 시작 기준이다. */
    val exam: List<ScenarioDevice>,
    /** 빈 교실 기록 시간. 앱은 2분이다. */
    val emptyRoomMs: Long = 120_000,
    /** 빈 교실 기록을 저장한 뒤 시험이 시작되기까지. */
    val breakMs: Long = 0,
    val examMs: Long = 600_000,
    /** 감시 화면을 몇 ms마다 다시 계산하는지. */
    val viewEveryMs: Long = 1_000,
    val seed: Long = 1,
)

/** 한 시각에 한 기기가 감시 화면 어디에 보였는지. */
enum class Shown { NEW, FAR, NONE }

data class DeviceResult(
    val label: String,
    /** 이 기기가 켜져 있던 동안 감시 화면을 계산한 횟수. */
    val ticks: Int,
    val newTicks: Int,
    val farTicks: Int,
    /** 켜진 뒤 처음 새 전파 목록에 오르기까지(ms). 오르지 않았으면 null. */
    val firstNewAfterMs: Long?,
    val received: Int,
) {
    val newRatio: Double get() = if (ticks == 0) 0.0 else newTicks.toDouble() / ticks
    val farRatio: Double get() = if (ticks == 0) 0.0 else farTicks.toDouble() / ticks
}

data class ScenarioResult(
    val devices: List<DeviceResult>,
    /** 시험 기기를 하나도 담지 않은 새 전파 묶음이 보인 횟수(감시 화면 계산 기준). 원래 있던 것을 잘못 알린 것이다. */
    val falseNewTicks: Int,
    val totalTicks: Int,
    val baseline: Map<String, Int>,
) {
    fun device(label: String): DeviceResult = devices.single { it.label == label }
}

object ScenarioRunner {
    private class Emission(val atMs: Long, val deviceIndex: Int, val obs: RfObservation)

    fun run(scenario: Scenario): ScenarioResult {
        val random = Random(scenario.seed)
        val examStartMs = scenario.emptyRoomMs + scenario.breakMs
        val examEndMs = examStartMs + scenario.examMs
        // 빈 교실 기기는 시험이 끝날 때까지 남아 있고, 시험 기기의 시각은 시험 시작 기준이다.
        val all = scenario.emptyRoom.map { it.copy(untilMs = minOf(it.untilMs, examEndMs)) } +
            scenario.exam.map { it.copy(fromMs = examStartMs + it.fromMs, untilMs = if (it.untilMs == Long.MAX_VALUE) examEndMs else minOf(examEndMs, examStartMs + it.untilMs)) }
        val emissions = all.flatMapIndexed { index, device -> emissions(index, device, random) }.sortedBy { it.atMs }

        val tracker = DeviceTracker()
        val received = IntArray(all.size)
        val addressOwner = HashMap<String, Int>()
        emissions.forEach { addressOwner[it.obs.key.uppercase(Locale.ROOT)] = it.deviceIndex }
        val examIndices = scenario.emptyRoom.size until all.size

        var next = 0
        fun feedUntil(timeMs: Long) {
            while (next < emissions.size && emissions[next].atMs <= timeMs) {
                val e = emissions[next++]
                tracker.update(e.obs)
                received[e.deviceIndex]++
            }
        }

        // 앱처럼 빈 교실 기록 끝에 그때까지 잡힌 것을 모두 저장한다.
        feedUntil(scenario.emptyRoomMs)
        val snapshot = SignalSnapshot.capture("scenario", scenario.emptyRoomMs, false, tracker.snapshot(scenario.emptyRoomMs, includeInactive = true))
        val baseline = baselineGroupCounts(snapshot)
        val baselineWifi = baselineWifiAddresses(snapshot)
        val samples = baselineSamples(snapshot)

        val ticks = IntArray(all.size)
        val newTicks = IntArray(all.size)
        val farTicks = IntArray(all.size)
        val firstNew = arrayOfNulls<Long>(all.size)
        var falseNew = 0
        var total = 0
        var now = examStartMs
        while (now <= examEndMs) {
            feedUntil(now)
            val view = watchView(tracker.snapshot(now, includeInactive = true), baseline, emptySet(), now, baselineWifi, samples)
            total++
            val shown = shownByDevice(view, addressOwner)
            if ((view.wifi + view.bluetooth).any { group -> group.devices.none { addressOwner[it.key.uppercase(Locale.ROOT)] in examIndices } }) falseNew++
            for (i in examIndices) {
                val d = all[i]
                if (now < d.fromMs || now > d.untilMs) continue
                ticks[i]++
                when (shown[i] ?: Shown.NONE) {
                    Shown.NEW -> { newTicks[i]++; if (firstNew[i] == null) firstNew[i] = now - d.fromMs }
                    Shown.FAR -> farTicks[i]++
                    Shown.NONE -> Unit
                }
            }
            now += scenario.viewEveryMs
        }
        return ScenarioResult(
            devices = examIndices.map { i -> DeviceResult(all[i].label, ticks[i], newTicks[i], farTicks[i], firstNew[i], received[i]) },
            falseNewTicks = falseNew,
            totalTicks = total,
            baseline = baseline,
        )
    }

    /**
     * 기기마다 지금 보이는 자리. 묶음에 주소가 하나라도 들어 있으면 그 묶음 자리에 보인 것으로 본다.
     * 앱은 묶음 단위로 알리므로, 같은 묶음에 든 시험 기기 둘은 한쪽 때문에 함께 보인 것으로 잡힌다.
     * 기기 하나의 결과를 보려면 같은 종류의 시험 기기를 한 시나리오에 하나만 넣는다.
     */
    private fun shownByDevice(view: WatchView, owner: Map<String, Int>): Map<Int, Shown> {
        val shown = HashMap<Int, Shown>()
        fun mark(groups: List<com.classroom.rfdetector.core.SignalGroup>, as_: Shown) = groups.forEach { group ->
            group.devices.forEach { device ->
                val i = owner[device.key.uppercase(Locale.ROOT)] ?: return@forEach
                if (shown[i] != Shown.NEW) shown[i] = as_
            }
        }
        mark(view.far, Shown.FAR)
        mark(view.wifi + view.bluetooth, Shown.NEW)
        return shown
    }

    private fun emissions(index: Int, device: ScenarioDevice, random: Random): List<Emission> {
        val out = ArrayList<Emission>()
        // 처음 받는 시각도 간격 안에서 무작위로 둔다. 모든 기기가 같은 순간에 잡히지 않게.
        var t = device.fromMs + (random.nextDouble() * device.receiveEveryMs).toLong()
        while (t <= device.untilMs) {
            if (random.nextDouble() >= device.lossRate) {
                val rssi = (device.rssi(t) + random.nextGaussian() * device.rssiNoiseDb).roundToInt().coerceIn(-126, -1)
                val obs = RfObservation(
                    key = address(index, device, t),
                    source = device.source,
                    name = device.advertisedName,
                    rssi = rssi,
                    txPower = null,
                    hidden = device.source == ScanSource.WIFI && device.advertisedName == null,
                    timestampMs = t,
                    manufacturerIds = device.manufacturerIds,
                    hints = device.hints,
                )
                out += Emission(t, index, obs)
            }
            val spread = 1.0 + (random.nextDouble() * 2 - 1) * device.jitter
            t += (device.receiveEveryMs * spread).toLong().coerceAtLeast(1)
        }
        return out
    }

    /** 기기 번호와 주소 순번으로 만든 가상 주소. 같은 기기의 주소도 서로 무관해 보이게 앞자리를 바꾼다. */
    private fun address(index: Int, device: ScenarioDevice, timeMs: Long): String {
        val rotation = device.rotateAddressEveryMs?.let { ((timeMs - device.fromMs) / it).toInt() } ?: 0
        return String.format(Locale.ROOT, "%02X:%02X:5C:00:%02X:%02X", 0xC0 or (rotation and 0x3F), index and 0xFF, (rotation shr 6) and 0xFF, index and 0xFF)
    }
}

/** 결과를 이슈에 붙일 수 있는 표로. */
fun ScenarioResult.markdownRows(): List<String> = devices.map { d ->
    val first = d.firstNewAfterMs?.let { "${it / 1000}초" } ?: "없음"
    "| ${d.label} | ${percent(d.newRatio)} | ${percent(d.farRatio)} | $first | ${d.received} |"
}

const val MARKDOWN_HEADER = "| 시험 기기 | 새 전파로 보인 비율 | 멀리서 약하게로 보인 비율 | 처음 새 전파로 보이기까지 | 받은 횟수 |\n| --- | --- | --- | --- | --- |"

private fun percent(ratio: Double) = "${(ratio * 100).roundToInt()}%"
