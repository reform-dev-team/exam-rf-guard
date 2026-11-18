package com.classroom.rfdetector.inspection.flow

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.SignalPresence
import com.classroom.rfdetector.core.TrackedDevice
import com.classroom.rfdetector.core.WEAK_SIGNAL_DBM
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

private const val SECOND_NS = 1_000_000_000L

internal fun walkSignalUsable(device: TrackedDevice): Boolean =
    device.source == ScanSource.BLE && device.presence == SignalPresence.RECENT &&
        (device.rssiEma ?: Double.NEGATIVE_INFINITY) >= WEAK_SIGNAL_DBM

/** Main-thread owner; the monitor's existing BLE scanner is the only radio source. */
internal class WalkCapture(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val _ui = MutableStateFlow(WalkUi())
    val ui = _ui.asStateFlow()
    private var walk: RoomWalk? = null
    private var deadline = 0L

    fun start(now: Long, radioReady: Boolean) {
        if (walk != null || !radioReady) return
        val sensor = accelerometer
        if (sensor == null || sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) != true) {
            _ui.value = _ui.value.copy(message = "가속도 센서를 사용할 수 없습니다.")
            return
        }
        walk = RoomWalk(now)
        deadline = now + MAX_WALK_NS
        _ui.value = WalkUi(available = true, active = true)
    }

    fun mark(now: Long, radioReady: Boolean) {
        tick(now, radioReady)
        val current = walk ?: return
        current.mark(now)
        publish(current, now)
    }

    fun observe(observation: RfObservation) {
        val time = observation.elapsedRealtimeNanos ?: return
        val rssi = observation.rssi ?: return
        if (observation.source == ScanSource.BLE && time < deadline) {
            walk?.observe(observation.trackingKey, time, rssi)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val current = walk ?: return
        if (event.timestamp >= deadline) return
        val (x, y, z) = event.values
        current.accelerate(event.timestamp, sqrt(x * x + y * y + z * z))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun tick(now: Long, radioReady: Boolean) {
        val current = walk
        if (current == null) {
            val available = radioReady && accelerometer != null
            val ui = _ui.value
            if (ui.available != available) _ui.value = ui.copy(available = available)
            return
        }
        if (!radioReady) {
            interrupt()
            return
        }
        current.advance(minOf(now, deadline))
        if (now >= deadline && current.result == null) {
            release()
            _ui.value = WalkUi(available = true, message = "측정 시간이 지났습니다. 처음부터 다시 시작하세요.")
            return
        }
        publish(current, now)
        if (current.result != null) release()
    }

    private fun publish(current: RoomWalk, now: Long) {
        val result = current.result
        val seconds = current.holdEndsAtNanos?.let { ((it - now).coerceAtLeast(0L) + SECOND_NS - 1) / SECOND_NS }?.toInt()
        val next = WalkUi(available = true, active = result == null, pointIndex = current.pointIndex,
            holdSeconds = seconds, result = result)
        if (next != _ui.value) _ui.value = next
    }

    fun interrupt() {
        if (walk != null) {
            release()
            _ui.value = WalkUi(message = "수신이 중단되었습니다. 처음부터 다시 시작하세요.")
        } else if (_ui.value.available) {
            _ui.value = _ui.value.copy(available = false)
        }
    }

    fun clear() {
        release()
        _ui.value = WalkUi()
    }

    private fun release() {
        if (walk != null) sensors?.unregisterListener(this)
        walk = null
    }
}
