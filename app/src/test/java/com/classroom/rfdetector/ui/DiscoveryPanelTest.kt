package com.classroom.rfdetector.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.classroom.rfdetector.core.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val peer = DiscoveryPeer("00:11:22:33:44:55", "Accessory", -60)

    @Test fun unknownBaselineDoesNotBecomeAClaimOfNewDevices() {
        compose.setContent { AppRfTheme {
            DiscoveryPanel(ScanSource.BLUETOOTH_DISCOVERY,
                DiscoveryState(DiscoveryStatus.COMPLETE, listOf(peer), 1000, 1000, usable = true), null, true, 1000)
        } }
        compose.onNodeWithText("이 경로의 빈 교실 기준 기록이 없습니다.", substring = true).assertExists()
        compose.onNodeWithText("빈 교실 때보다 늘어난 후보", substring = true).assertDoesNotExist()
        compose.onNodeWithText("탐색 목록 1건 · 펼치기").performClick()
        compose.onNodeWithText("Accessory · 1건").assertExists()
        compose.onNodeWithText("가까울수록 빠른 진동").assertDoesNotExist()
    }

    @Test fun failedResultRemainsVisibleWithoutACurrentCandidateCount() {
        compose.setContent { AppRfTheme {
            DiscoveryPanel(ScanSource.BLUETOOTH_DISCOVERY,
                DiscoveryState(DiscoveryStatus.FAILED, listOf(peer), 1000, 1000), emptyMap(), true, 2000)
        } }
        compose.onNodeWithText("추가 탐색 실패 · 이전 또는 부분 결과").assertExists()
        compose.onNodeWithText("탐색 목록 1건 · 펼치기").assertExists()
        compose.onNodeWithText("빈 교실 때보다 늘어난 후보", substring = true).assertDoesNotExist()
    }

    @Test fun currentSuccessfulRoundComparesAgainstAnExplicitEmptyBaseline() {
        compose.setContent { AppRfTheme {
            DiscoveryPanel(ScanSource.BLUETOOTH_DISCOVERY,
                DiscoveryState(DiscoveryStatus.COMPLETE, listOf(peer), 1000, 1000, usable = true), emptyMap(), true, 2000)
        } }
        compose.onNodeWithText("빈 교실 때보다 늘어난 후보 1종").assertExists()
    }
}
