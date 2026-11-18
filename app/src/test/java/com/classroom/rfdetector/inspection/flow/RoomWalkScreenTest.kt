package com.classroom.rfdetector.inspection.flow

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.classroom.rfdetector.core.DistanceClass
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.SignalPresence
import com.classroom.rfdetector.core.TrackedDevice
import com.classroom.rfdetector.ui.AppRfTheme
import com.classroom.rfdetector.ui.RfColors
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomWalkScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun device(address: String, presence: SignalPresence, rssi: Double) = TrackedDevice(
        key = address, displayName = "동일한 이름", source = ScanSource.BLE, hidden = false,
        rssiEma = rssi, distanceClass = DistanceClass.UNKNOWN, firstSeenMs = 0, lastSeenMs = 0,
        presence = presence,
    )

    @Test fun walkControlsAndExplicitExactSignalSelection() {
        val first = device("AA:BB:CC:DD:EE:01", SignalPresence.RECENT, -90.0)
        val second = device("AA:BB:CC:DD:EE:02", SignalPresence.HISTORY, -60.0)
        val state = mutableStateOf(WalkUi(available = true))
        var started = 0
        var marked = 0
        var selected: String? = null
        compose.setContent {
            AppRfTheme { Surface(color = RfColors.bg) {
                RoomWalkScreen(state.value, listOf(first, second), { started++ }, { marked++ }, { selected = it })
            } }
        }
        saveScreen("room-walk-phone")
        scrollTo("교실 측정 시작").performClick()
        assertEquals(1, started)
        compose.runOnIdle { state.value = WalkUi(available = true, active = true) }
        scrollTo("이 지점에서 측정").performClick()
        assertEquals(1, marked)
        compose.runOnIdle { state.value = WalkUi(available = true, active = true, holdSeconds = 8) }
        scrollTo("이 지점에서 측정").assertIsNotEnabled()
        compose.onNodeWithText("제자리에서 측정 중 · 8초 남음").assertIsDisplayed()
        compose.runOnIdle {
            state.value = WalkUi(available = true, result = listOf(WalkCandidate(first.trackingKey, null), WalkCandidate(second.trackingKey, null)))
        }
        scrollTo("지도에 표시할 수 있는 신호가 없습니다.").assertIsDisplayed()
        scrollTo("각 지점에서는 8초 동안 움직이지 말고 다시 측정하세요.", substring = true).assertIsDisplayed()
        scrollTo("위치를 추정하지 못한 신호 (2)").assertIsDisplayed()
        scrollTo("주소: ${first.key}").assertIsDisplayed()
        scrollTo("주소: ${second.key}").assertIsDisplayed()
        scrollTo("현재 수신 중 · 약한 신호 · 구역을 추정할 수 없음").assertIsDisplayed()
        scrollTo("현재 수신되지 않음 · 구역을 추정할 수 없음").assertIsDisplayed()
        assertEquals(null, selected)
        compose.onNodeWithTag("room-walk").performScrollToNode(hasContentDescription("${second.key} 신호 찾기"))
        compose.onNodeWithContentDescription("${second.key} 신호 찾기").performClick()
        assertEquals(second.trackingKey, selected)
        compose.runOnIdle { state.value = WalkUi(available = true, result = emptyList()) }
        scrollTo("측정된 신호가 없습니다.").assertIsDisplayed()
        scrollTo("다시 측정").performClick()
        assertEquals(2, started)
    }

    @Test fun completionShowsFittedPinsAndDistinguishesOverlappingAddresses() {
        val addresses = (1..5).map { "02:00:00:00:00:0$it" }
        val devices = addresses.map { device(it, SignalPresence.RECENT, -58.0) }
        val fits = listOf(
            listOf(-47, -75, -75, -75, -49),
            listOf(-75, -48, -75, -75, -75),
            listOf(-75, -75, -48, -75, -75),
            listOf(-49, -75, -75, -75, -47),
            List(5) { -62 },
        )
        val walk = RoomWalk(0)
        for (point in 0..4) {
            val start = point * 10_000_000_000L
            walk.mark(start)
            for (offset in 1..2) for ((index, device) in devices.withIndex()) {
                walk.observe(device.trackingKey, start + offset * 1_000_000_000L, fits[index][point])
            }
            walk.advance(start + WALK_HOLD_NANOS)
        }

        var picked: String? = null
        val state = mutableStateOf(WalkUi(available = true, active = true, pointIndex = 4, holdSeconds = 1))
        compose.setContent {
            AppRfTheme { Surface(color = RfColors.bg) { RoomWalkScreen(state.value, devices, {}, {}, { picked = it }) } }
        }
        scrollTo("이 지점에서 측정").assertIsNotEnabled()
        compose.runOnIdle { state.value = WalkUi(available = true, pointIndex = 5, result = checkNotNull(walk.result)) }
        compose.onNodeWithTag("walk-result-map").assertIsDisplayed()
        val topLeft = compose.onNodeWithContentDescription("${addresses[1]} 신호 찾기").assertIsDisplayed()
        val topRight = compose.onNodeWithContentDescription("${addresses[2]} 신호 찾기").assertIsDisplayed()
        val cluster = compose.onNodeWithContentDescription("겹친 신호 2개", substring = true).assertIsDisplayed()
        assertTrue(topLeft.getUnclippedBoundsInRoot().right < topRight.getUnclippedBoundsInRoot().left)
        assertTrue(topLeft.getUnclippedBoundsInRoot().bottom < cluster.getUnclippedBoundsInRoot().top)
        compose.onNode(hasContentDescription("${addresses[4]} 신호 찾기") and hasAnyAncestor(hasTestTag("walk-result-map")))
            .assertDoesNotExist()
        saveScreen("room-walk-result-map")

        topRight.performTouchInput { click() }
        assertEquals(devices[2].trackingKey, picked)
        cluster.performTouchInput { click() }
        assertEquals(devices[2].trackingKey, picked)
        compose.onNodeWithText("겹친 신호 선택").assertIsDisplayed()
        compose.onNodeWithContentDescription("${addresses[0]} 신호 찾기").assertIsDisplayed()
        compose.onNodeWithContentDescription("${addresses[3]} 신호 찾기").performTouchInput { click() }
        assertEquals(devices[3].trackingKey, picked)
    }

    @Config(qualifiers = "w320dp-h640dp-mdpi")
    @Test fun largeTextKeepsStartControlAccessible() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                AppRfTheme { Surface(color = RfColors.bg) {
                    RoomWalkScreen(WalkUi(available = true), emptyList(), {}, {}, {})
                } }
            }
        }
        saveScreen("room-walk-large-text")
        scrollTo("교실 측정 시작").assertIsDisplayed()
    }

    private fun scrollTo(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        compose.onNodeWithTag("room-walk").performScrollToNode(hasText(text, substring = substring))
        return compose.onNodeWithText(text, substring = substring)
    }

    private fun saveScreen(name: String) {
        val target = File("build/outputs/ui-captures/$name.png")
        target.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().lastOrNull() ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
