package com.classroom.rfdetector.core

import kotlin.math.pow

/** RSSI 평활, 거리 환산, 거리 등급 분류 */
object RssiFilter {
    /** 보정 정보가 없을 때 사용하는 1m 기준 RSSI 가정값. BLE 표준 보정값은 아니다. */
    const val DEFAULT_TX_DBM = -59.0

    /**
     * 와이파이 접속점(공유기·휴대폰 핫스팟)의 1m 기준 RSSI 가정값. 실측값이 아닌 추정이다.
     * 핫스팟·공유기는 보통 블루투스 저전력 기기보다 15~20 dB 세게 송신하므로 블루투스 가정값(-59)에
     * 그만큼 더한 값으로 잡았다. 블루투스 값을 그대로 쓰면 교실 안 핫스팟이 거의 늘 "바로 옆"으로 나온다(#37).
     * 기기·대역마다 크게 다르므로 실측이 모이면 다시 정한다.
     */
    const val WIFI_DEFAULT_TX_DBM = -40.0

    /** 광고에 보정값이 없을 때 쓰는 1m 기준값. 전파 방식마다 송신 세기가 달라 따로 둔다. */
    fun defaultReferenceAt1m(source: ScanSource): Double =
        if (source == ScanSource.WIFI) WIFI_DEFAULT_TX_DBM else DEFAULT_TX_DBM
    /** 실내 경로손실 지수 */
    const val PATH_N = 2.7

    /** 지수 이동평균 */
    fun ema(prev: Double?, sample: Int, alpha: Double = 0.25): Double =
        prev?.let { it + alpha * (sample - it) } ?: sample.toDouble()

    /** 로그 거리 경로손실 모델 */
    fun distanceM(rssi: Double, txDbm: Double = DEFAULT_TX_DBM): Double =
        10.0.pow((txDbm - rssi) / (10 * PATH_N))

    fun classify(distance: Double?): DistanceClass = when {
        distance == null -> DistanceClass.UNKNOWN
        distance < 1.6 -> DistanceClass.VERY_CLOSE
        distance < 3.2 -> DistanceClass.CLOSE
        distance < 8.0 -> DistanceClass.NEAR
        else -> DistanceClass.FAR
    }
}
