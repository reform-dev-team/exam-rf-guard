package com.classroom.rfdetector.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import com.classroom.rfdetector.App
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.sim.RadioSimulator
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34, 35], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReadabilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun deviceNameStaysCompleteAndVibrationRowIsLabeledAndToggleable() {
        val name = "김민수의 AirPods Pro"
        compose.setContent { AppRfTheme { App() } }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        try {
            compose.runOnIdle { vm.setSim(true) }
            compose.onNodeWithText("빈 교실 측정 시작").performClick()
            compose.waitUntil(10_000) { vm.devices.value.size == 5 && !vm.snapshotBusy.value }
            compose.onNodeWithText("빈 교실 기록 저장").assertIsEnabled().performClick()
            waitForText("새 전파 없음")
            compose.runOnIdle {
                vm.sim.devices.add(RadioSimulator.VDev("sim-extra-1", name, ScanSource.BLE, false, 1.0, 1.0))
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            }
            waitForText("새 전파 1개 발견")
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("무선 이어폰"))
            compose.onNodeWithText("무선 이어폰").performClick()

            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(name).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.size)
            val layout = layouts.single()
            assertEquals(name.length, layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
            assertTrue((0 until layout.lineCount).all { layout.getLineRight(it) <= layout.size.width + 1f })
            assertTrue((0 until layout.lineCount).none(layout::isLineEllipsized))
            assertTrue(layout.layoutInput.style.fontSize.value >= 16f)

            val vibration = compose.onNodeWithText("가까울수록 빠른 진동")
            vibration.performScrollTo().assertHasClickAction().assertIsOff()
            val bounds = vibration.fetchSemanticsNode().boundsInRoot
            val minimum = 48f * compose.activity.resources.displayMetrics.density
            assertTrue(bounds.width >= minimum && bounds.height >= minimum)
            vibration.performClick().assertIsOn()
            compose.runOnIdle {
                assertTrue(vm.geigerOn.value)
                assertEquals(vm.devices.value.first { it.displayName == name }.trackingKey, vm.findTarget.value)
            }
            vibration.performClick().assertIsOff()
            compose.runOnIdle { assertFalse(vm.geigerOn.value) }
        } finally { vm.shutdown() }
    }

    private fun waitForText(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
