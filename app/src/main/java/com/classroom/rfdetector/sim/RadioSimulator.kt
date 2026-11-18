package com.classroom.rfdetector.sim

import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import java.util.Random
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max

/**
 * 에뮬레이터/실기기 검증용 가상 RF 환경.
 * 실제 스캐너와 동일한 관측 형식(RfObservation)을 만들어 같은 파이프라인을 탄다.
 * 좌표계: 북=+y, 동=+x. 사용자는 원점에 있다.
 */
class RadioSimulator(private val rnd: Random = Random()) {
    class VDev(
        val key: String,
        val name: String,
        val source: ScanSource,
        val hidden: Boolean,
        var x: Double,
        var y: Double,
        val txDbm: Double = -59.0,
    )

    val devices = mutableListOf(
        VDev("sim-airpods", "AirPods Pro (가상)", ScanSource.BLE, false, x = 1.3, y = 2.6),
        VDev("sim-buds", "Galaxy Buds (가상)", ScanSource.BLE, false, x = -2.6, y = 1.4),
        VDev("sim-watch", "스마트워치 (가상)", ScanSource.BLE, false, x = 0.5, y = 0.9),
        VDev("sim-hotspot", "휴대폰 핫스팟 (가상)", ScanSource.WIFI, false, x = 3.8, y = -4.2),
        VDev("sim-hidden", "(이름 없는 AP · 가상)", ScanSource.WIFI, true, x = -4.6, y = -3.2),
    )


    fun reset() {
        devices.removeAll { it.key.startsWith("sim-extra-") }
    }

    fun addDevice() {
        val index = devices.count { it.key.startsWith("sim-extra-") } + 1
        devices.add(VDev("sim-extra-$index", "AirPods Pro · 추가 가상 $index", ScanSource.BLE, false, 1.0, 1.0))
    }

    /** 4Hz 정도로 호출: 원점 기준 관측값 생성 */
    fun tick(now: Long = System.currentTimeMillis()): List<RfObservation> {
        return devices.map { d ->
            val dist = max(0.4, hypot(d.x, d.y))
            var rssi = d.txDbm - 10 * 2.7 * log10(dist) + rnd.nextGaussian() * 3.2
            if (rnd.nextDouble() < 0.08) rssi -= 5.0   // 다중경로 페이딩 흉내
            RfObservation(
                key = d.key,
                source = d.source,
                name = d.name.takeIf { !d.hidden },
                rssi = rssi.toInt(),
                txPower = null,
                hidden = d.hidden,
                timestampMs = now,
                manufacturerIds = when {
                    d.key == "sim-airpods" || d.key.startsWith("sim-extra-") -> setOf(76)
                    d.key == "sim-buds" -> setOf(117)
                    else -> emptySet()
                },
            )
        }
    }
}
