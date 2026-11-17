package com.classroom.rfdetector.core

/** 휴대폰 배터리 상태. levelPercent는 0~100이며 모르면 null이다. */
data class BatteryState(val levelPercent: Int?, val charging: Boolean)

/** 이 아래로 떨어지면 "곧 꺼질 수 있다"고 더 강하게 알린다. */
const val LOW_BATTERY_PERCENT = 20

enum class BatteryWarning { NONE, NOT_CHARGING, LOW }

/**
 * 시험 내내 화면을 켜 두므로 배터리가 빨리 닳는다. 배터리가 꺼지면 감시가 알림 없이 멈추므로
 * 충전기가 빠져 있으면 알린다. 충전 중이거나 상태를 모르면 알리지 않는다.
 */
fun batteryWarning(state: BatteryState?): BatteryWarning = when {
    state == null || state.charging -> BatteryWarning.NONE
    state.levelPercent != null && state.levelPercent <= LOW_BATTERY_PERCENT -> BatteryWarning.LOW
    else -> BatteryWarning.NOT_CHARGING
}

/**
 * ACTION_BATTERY_CHANGED의 값으로 상태를 만든다. plugged가 없으면(-1) 상태를 모르는 것으로 본다.
 * 충전 여부는 status가 아니라 전원 연결(plugged)로 판단한다. 가득 차서 충전을 멈춘 상태도 연결돼 있으면 괜찮다.
 */
fun batteryState(level: Int, scale: Int, plugged: Int): BatteryState? {
    if (plugged < 0) return null
    val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
    return BatteryState(percent, plugged != 0)
}
