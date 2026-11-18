package com.classroom.rfdetector.core

import android.content.Context
import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowSystemVibrator

@Implements(className = "android.os.SystemVibrator")
class InFlightVibrator : ShadowSystemVibrator() {
    companion object {
        var entered = CountDownLatch(1)
        var release = CountDownLatch(1)
        val operations: MutableList<String> = Collections.synchronizedList(mutableListOf())
    }

    @Implementation(minSdk = 29, maxSdk = 30)
    override fun vibrate(uid: Int, opPkg: String, effect: VibrationEffect, reason: String?, attributes: AudioAttributes?) {
        entered.countDown()
        check(release.await(5, TimeUnit.SECONDS))
        operations.add("pulse")
        super.vibrate(uid, opPkg, effect, reason, attributes)
    }

    @Implementation
    override fun cancel() {
        operations.add("cancel")
        super.cancel()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], shadows = [InFlightVibrator::class])
class GeigerStopTest {
    @Test fun stopCancelsAnInFlightPulseBeforeReturning() {
        InFlightVibrator.entered = CountDownLatch(1)
        InFlightVibrator.release = CountDownLatch(1)
        InFlightVibrator.operations.clear()
        val app = RuntimeEnvironment.getApplication()
        val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val controller = GeigerController(app)
        val stopStarted = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val stopper = Thread {
            stopStarted.countDown()
            try { controller.stop() } finally { stopReturned.countDown() }
        }
        try {
            controller.setClass(DistanceClass.VERY_CLOSE)
            controller.start()
            assertTrue("Proximity pulse reached the native vibrator", InFlightVibrator.entered.await(5, TimeUnit.SECONDS))
            stopper.start()
            assertTrue(stopStarted.await(5, TimeUnit.SECONDS))
            // Let stop either finish (the old race) or wait for the in-flight native call.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (stopReturned.count != 0L && stopper.state != Thread.State.BLOCKED) {
                check(System.nanoTime() < deadline) { "Stop did not reach the in-flight pulse" }
                Thread.yield()
            }
            InFlightVibrator.release.countDown()
            stopper.join(5_000)
            assertFalse("Stop must complete after the native call is released", stopper.isAlive)
            assertEquals(listOf("pulse", "cancel"), InFlightVibrator.operations.toList())
            assertFalse("Sampling must not begin with an uncancelled motor pulse", shadowOf(vibrator).isVibrating)
        } finally {
            InFlightVibrator.release.countDown()
            stopper.join(5_000)
            controller.stop()
        }
    }
}
