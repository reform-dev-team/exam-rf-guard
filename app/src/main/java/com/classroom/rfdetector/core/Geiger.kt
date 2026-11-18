package com.classroom.rfdetector.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 지예거 모드: 화면을 안 봐도 진동 주기로 근접을 알린다.
 * 바로 옆 350ms / 가까움 800ms / 중간 1.6s / 멀다 3s 주기로 짧게 울린다.
 */
class GeigerController(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    @Volatile
    private var cls: DistanceClass = DistanceClass.UNKNOWN

    fun setClass(c: DistanceClass) {
        cls = c
    }

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val period = when (cls) {
                    DistanceClass.VERY_CLOSE -> 350L
                    DistanceClass.CLOSE -> 800L
                    DistanceClass.NEAR -> 1600L
                    DistanceClass.FAR -> 3000L
                    DistanceClass.UNKNOWN -> 0L
                }
                if (period == 0L) {
                    delay(400)
                    continue
                }
                val dur = if (cls == DistanceClass.VERY_CLOSE) 130L else 60L
                // An in-flight pulse must finish before stop() cancels it.
                synchronized(this@GeigerController) {
                    if (isActive) runCatching {
                        vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
                delay(period)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        runCatching { vibrator.cancel() }
    }
}
