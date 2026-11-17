package com.classroom.rfdetector.inspection.radio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.classroom.rfdetector.App
import com.classroom.rfdetector.AppViewModel
import androidx.lifecycle.Lifecycle
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.ui.AppRfTheme
import com.classroom.rfdetector.ui.RfColors
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioSurveyFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun normalPhoneSyntheticWifiJourney() = journey(1f, "survey-phone")

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun largeTextSyntheticWifiJourney() = journey(1.5f, "survey-large-text")

    private fun journey(fontScale: Float, screenshot: String) {
        val model = RoomSurvey("WIFI:00:11:22:33:44:55", ScanSource.WIFI)
        var now by mutableLongStateOf(2_000_000_000L)
        var selected by mutableStateOf(Checkpoint.FRONT_LEFT)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                AppRfTheme { Surface(Modifier.fillMaxSize(), color = RfColors.bg) {
                    RadioSurveyPanel("합성 와이파이", ScanSource.WIFI, model, now, true, "새 결과 대기", selected,
                        onSelectPoint = { selected = it }, onStart = {}, onStop = {},
                        onCapture = { model.begin(selected, now); now++ },
                        onFinish = { model.finish(now); selected = model.nextPoint ?: selected; now++ }, onCancel = { model.cancelCapture(); now++ },
                        onReference = { model.setReference(now); now++ }, onReset = { model.reset(); now++ }, onChoose = {})
                } }
            }
        }
        Checkpoint.entries.forEachIndexed { index, point ->
            compose.onNodeWithText("이곳에서 새 측정 시작").performScrollTo().performClick()
            compose.onNodeWithText("이 지점 기록").performScrollTo().assertIsNotEnabled()
            compose.runOnIdle {
                now += 1_000_000_000
                model.accept(model.trackingKey, now, if (point == Checkpoint.FRONT_LEFT || point == Checkpoint.BACK_LEFT) -45 else -75, now)
            }
            compose.onNodeWithText("이 지점 기록").assertIsEnabled().performClick()
            if (index == 0) compose.onNodeWithText("지금 세기를 출발값으로").performScrollTo().performClick()
            if (index == 1) {
                compose.runOnIdle {
                    now += 30_000_000_000L
                    model.accept(model.trackingKey, now, -75, now)
                }
                compose.onNodeWithText(Approach.WEAKER.label).performScrollTo().assertIsDisplayed()
            }
        }
        assertTrue(model.tentative)
        assertTrue(model.readings.values.all { it.dispersion == null })
        assertEquals(StrongerArea.LEFT, model.strongerArea())
        compose.onNodeWithText("다섯 곳 비교 · 5/5 기록").performScrollTo().assertIsDisplayed()
        save("$screenshot-map")
        compose.onNodeWithText("세기가 더 강했던 쪽: 왼편").performScrollTo().assertIsDisplayed()
        save("$screenshot-result")
        compose.onNodeWithText("다섯 곳 기록 초기화").performScrollTo().performClick()
        assertTrue(model.readings.isEmpty())
    }

    @Test fun returningFromSurveyDoesNotStopMonitoring() {
        compose.setContent { AppRfTheme { App() } }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        try {
            compose.runOnIdle { vm.setSim(true); vm.startScan() }
            compose.waitUntil(10_000) { vm.devices.value.isNotEmpty() }
            compose.onNodeWithText("다섯 곳 비교").performClick()
            compose.onNodeWithText("다섯 곳 신호 비교").assertExists()
            compose.onNodeWithText("AirPods Pro (가상)", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("선택한 신호 받기").assertIsNotEnabled()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(vm.scanning.value)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("선택한 신호 받기").assertIsNotEnabled()
            compose.onNodeWithContentDescription("돌아가기").performClick()
            assertTrue(vm.scanning.value)
        } finally { vm.shutdown() }
    }

    @Test fun transientSurveyCandidateRemainsReviewableInRealApp() = transientJourney(1f, "survey-transient")

    @Test @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun transientSurveyAlertRemainsReviewableWithLargeText() = transientJourney(1.5f, "survey-transient-large")

    private fun transientJourney(fontScale: Float, screenshot: String) {
        var clockNow = System.currentTimeMillis()
        val vm = ViewModelProvider(compose.activity, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(compose.activity.applicationContext, nowMs = { clockNow }) as T
        })[AppViewModel::class.java]
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { AppRfTheme { App() } }
        }
        try {
            compose.runOnIdle { vm.setSim(true) }
            compose.onNodeWithText("빈 교실 측정 시작").performClick()
            awaitState { vm.devices.value.isNotEmpty() && !vm.snapshotBusy.value }
            compose.onNodeWithText("빈 교실 기록 저장").assertIsEnabled().performClick()
            awaitState { vm.baselineSet.value && !vm.snapshotBusy.value }
            compose.onNodeWithText("다섯 곳 비교").performClick()
            compose.onNodeWithText("다섯 곳 신호 비교").assertIsDisplayed()
            compose.runOnIdle {
                vm.tracker.update(RfObservation("12:34:56:78:90:AA", ScanSource.WIFI,
                    "합성 약한 신호", -100, null, false, clockNow))
            }
            awaitState { vm.watch.value.far.any { it.strongest.key == "12:34:56:78:90:AA" } }
            compose.onNodeWithText("새 신호 0개").assertDoesNotExist()
            assertTrue(vm.surveyNotices.value.isEmpty())
            compose.runOnIdle {
                vm.tracker.update(RfObservation("12:34:56:78:90:AB", ScanSource.WIFI,
                    "합성 임시 신호", -52, null, false, clockNow,
                    details = mapOf("합성 정보" to "기록된 값")))
            }
            awaitState { vm.surveyNotices.value.any { it.key == "12:34:56:78:90:AB" } }
            compose.onNodeWithText("새 신호 1개").assertIsDisplayed()
            assertEquals(1, vm.surveyNotices.value.size)
            save("$screenshot-warning")
            compose.runOnIdle { clockNow += 100_000L }
            awaitState { vm.watch.value.wifi.none { it.strongest.key == "12:34:56:78:90:AB" } }
            compose.onNodeWithContentDescription("돌아가기").performClick()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("합성 임시 신호", substring = true))
            compose.onNodeWithText("합성 임시 신호", substring = true).performScrollTo()
            save("$screenshot-return")
            compose.onNodeWithText("합성 임시 신호", substring = true).assertIsDisplayed()
            compose.onNodeWithText("지금 받지 못함", substring = true).assertIsDisplayed()
            compose.onNodeWithText("합성 임시 신호", substring = true).performClick()
            compose.onNodeWithText("비교 중 발견 · 지금 받지 못함").assertIsDisplayed()
            compose.onNodeWithText("기록된 값").performScrollTo().assertIsDisplayed()
            save("$screenshot-detail")
            compose.onNodeWithText("확인함\n목록에서 빼기").performClick()
            assertTrue(vm.surveyNotices.value.isEmpty())
        } finally { vm.shutdown() }
    }

    private fun awaitState(condition: () -> Boolean) = compose.waitUntil(10_000) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        condition()
    }

    private fun save(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().first { it.width > 0 && it.height > 0 }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/outputs/ui-captures/$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
