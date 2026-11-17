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
        compose.onNodeWithText("이 경로에는 비교에 필요한 기준 정보가 없습니다.", substring = true).assertExists()
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

    @Test fun p2pListsNeverOfferStrengthOrDistanceEvenIfAnInvalidSampleContainsRssi() {
        compose.setContent { AppRfTheme {
            DiscoveryPanel(ScanSource.WIFI_P2P,
                DiscoveryState(DiscoveryStatus.COMPLETE, listOf(peer), 1000, 1000, usable = true), emptyMap(), true, 2000)
        } }
        compose.onNodeWithText("Wi-Fi Direct 탐색").assertExists()
        compose.onNodeWithText("탐색 목록 1건 · 펼치기").performClick()
        compose.onNodeWithText("탐색 때 세기", substring = true).assertDoesNotExist()
        compose.onNodeWithText("실제 패킷 수신 시각·세기·거리는 알 수 없으며", substring = true).assertExists()
    }

    @Test fun aLateNameIsDisplayedWhileItsBaselineAddressPreventsANewCandidate() {
        val source = ScanSource.WIFI_P2P
        val tracker = DiscoveryTracker(listOf(source))
        tracker.finish(source, DiscoveryRound(DiscoveryStatus.COMPLETE, listOf(peer.copy(name = null)), 1000), collecting = true)
        val snapshot = SignalSnapshot("Room", 1000, false, emptyList(), tracker.baselines, tracker.baselineAddresses)
        compose.setContent { AppRfTheme {
            DiscoveryPanel(source, DiscoveryState(DiscoveryStatus.COMPLETE, listOf(peer), 2000, 2000, usable = true),
                snapshot.discoveryBaseline(source), true, 2000, snapshot.discoveryBaselineAddresses.getValue(source))
        } }
        compose.onNodeWithText("빈 교실 때보다 늘어난 후보 0종").assertExists()
        compose.onNodeWithText("탐색 목록 1건 · 펼치기").performClick()
        compose.onNodeWithText("Accessory · 1건").assertExists()
    }

    @Test fun aBaselineGroupWhoseAddressesNowHaveDifferentNamesDisplaysBothCurrentNames() {
        val source = ScanSource.WIFI_P2P
        val tracker = DiscoveryTracker(listOf(source))
        val original = listOf(peer.copy(name = null), DiscoveryPeer("00:11:22:33:44:66", null))
        tracker.finish(source, DiscoveryRound(DiscoveryStatus.COMPLETE, original, 1000), collecting = true)
        val current = listOf(peer, original.last().copy(name = "Other accessory"))
        compose.setContent { AppRfTheme {
            DiscoveryPanel(source, DiscoveryState(DiscoveryStatus.COMPLETE, current, 2000, 2000, usable = true),
                tracker.baselines.getValue(source), true, 2000, tracker.baselineAddresses.getValue(source))
        } }
        compose.onNodeWithText("빈 교실 때보다 늘어난 후보 0종").assertExists()
        compose.onNodeWithText("탐색 목록 2건 · 펼치기").performClick()
        compose.onNodeWithText("이름이 다른 탐색 결과 · 2건").assertExists()
        compose.onNodeWithText("Accessory").assertExists()
        compose.onNodeWithText("Other accessory").assertExists()
    }
}
