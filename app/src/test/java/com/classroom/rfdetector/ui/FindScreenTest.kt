package com.classroom.rfdetector.ui

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.classroom.rfdetector.AppViewModel
import com.classroom.rfdetector.core.BleAddressType
import com.classroom.rfdetector.core.RfObservation
import com.classroom.rfdetector.core.ScanSource
import com.classroom.rfdetector.core.SignalHint
import com.classroom.rfdetector.core.SignalPresence
import com.classroom.rfdetector.core.SignalSnapshot
import com.classroom.rfdetector.core.SnapshotSignal
import com.classroom.rfdetector.core.acknowledgeKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FindScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val directory = TemporaryFolder()
    private lateinit var vm: AppViewModel
    private val baselineAddress = "00:11:22:AA:BB:CC"

    @Before fun create() {
        compose.runOnIdle {
            val app = compose.activity.applicationContext
            vm = AppViewModel(object : ContextWrapper(app) {
                override fun getFilesDir() = directory.root
            })
            vm.setSim(true)
            vm.baselineSnapshot.value = SignalSnapshot(
                "시험실", System.currentTimeMillis(), true,
                listOf(SnapshotSignal(baselineAddress, ScanSource.BLE, "Recorded beacon", -60.0, "제조사 미확인")),
            )
            // Apply the baseline through the normal session setup, then supply observations directly.
            vm.startScan()
            vm.stopScan()
            vm.tracker.clear()
        }
    }

    @After fun close() { compose.runOnIdle { vm.shutdown() } }

    private fun observation(
        address: String = "AA:BB:CC:DD:EE:01",
        source: ScanSource = ScanSource.BLE,
        hints: Set<SignalHint> = emptySet(),
    ) = RfObservation(address, source, "New test signal", -60, null, false, System.currentTimeMillis(), hints = hints)

    private fun replaceObservation(observation: RfObservation) {
        compose.runOnIdle {
            vm.tracker.clear()
            vm.tracker.update(observation)
            // An unrelated acknowledgement publishes the tracker's current snapshot.
            vm.acknowledge("test:refresh")
        }
    }

    private fun show(observation: RfObservation) {
        compose.setContent { AppRfTheme { FindScreen(vm, observation.trackingKey, onBack = {}) } }
        compose.onNodeWithText("기기 정보").assertExists()
    }

    @Test fun bluetoothAddressLookupRowFollowsTheReportedAddressType() {
        val address = "58:AD:12:34:56:78"
        val random = observation(address = address).copy(bleAddressType = BleAddressType.RANDOM)
        replaceObservation(random)
        show(random)
        compose.onNodeWithText("임의 주소라 조회할 수 없음. 휴대폰과 이어폰은 주소를 주기적으로 바꿉니다.").assertExists()
        compose.onNodeWithText("등록부에 없음").assertDoesNotExist()

        val unknown = observation(address = address)
        replaceObservation(unknown)
        compose.onNodeWithText("주소 유형을 확인할 수 없어 조회하지 않음").assertExists()
        compose.onNodeWithText("등록부에 없음").assertDoesNotExist()
    }

    @Test fun recordedAddressKeepsItsLabelRegardlessOfAddressCase() {
        val signal = observation(address = baselineAddress.lowercase()).copy(name = "Recorded beacon")
        replaceObservation(signal)
        show(signal)

        compose.onNodeWithText("빈 교실 기록에 있던 전파").assertExists()
        compose.onNodeWithText("확인함\n목록에서 빼기").assertDoesNotExist()
    }

    @Test fun anotherAddressInTheRecordedGroupIsNotLabeledAsRecorded() {
        val signal = observation().copy(name = "Recorded beacon")
        replaceObservation(signal)
        assertTrue(vm.watch.value.bluetooth.isEmpty())
        assertEquals(signal.trackingKey, vm.watch.value.knownGroups.single().live.single().trackingKey)
        show(signal)

        compose.onNodeWithText("빈 교실 기록에 있던 전파").assertDoesNotExist()
    }

    @Test fun disappearingNewSignalDoesNotBecomeAnEmptyRoomSignal() {
        val signal = observation()
        replaceObservation(signal)
        assertEquals(1, vm.watch.value.bluetooth.size)
        show(signal)
        compose.onNodeWithText("확인함\n목록에서 빼기").assertExists()

        // Use a past capture time instead of assuming Robolectric advances the JVM wall clock.
        replaceObservation(signal.copy(timestampMs = System.currentTimeMillis() - 16_000))

        assertEquals(SignalPresence.HISTORY, vm.devices.value.single().presence)
        assertTrue(vm.watch.value.bluetooth.isEmpty())
        compose.onNodeWithText("안 잡힘").assertExists()
        compose.onNodeWithText("빈 교실 기록에 있던 전파").assertDoesNotExist()
        compose.onNodeWithText("확인함\n목록에서 빼기").assertDoesNotExist()
    }

    @Test fun newFindMySignalIsNotLabeledAsRecordedInTheEmptyRoom() {
        val signal = observation(hints = setOf(SignalHint.APPLE_FIND_MY))
        replaceObservation(signal)
        assertEquals(signal.trackingKey, vm.watch.value.findMy.single().trackingKey)
        show(signal)

        compose.onNodeWithText("빈 교실 기록에 있던 전파").assertDoesNotExist()
        compose.onNodeWithText("확인함\n목록에서 빼기").assertDoesNotExist()
    }

    @Test fun acknowledgingANewSignalDoesNotAddItToTheEmptyRoomRecord() {
        val signal = observation()
        replaceObservation(signal)
        compose.runOnIdle { vm.acknowledge(vm.devices.value.single().acknowledgeKey) }
        assertTrue(vm.watch.value.knownGroups.any { it.sample == null && it.last?.trackingKey == signal.trackingKey })
        show(signal)

        compose.onNodeWithText("빈 교실 기록에 있던 전파").assertDoesNotExist()
        compose.onNodeWithText("확인함\n목록에서 빼기").assertDoesNotExist()
    }

    @Test fun theSameAddressOnAnotherRadioIsNotAnEmptyRoomRecord() {
        val signal = observation(address = baselineAddress, source = ScanSource.WIFI)
        replaceObservation(signal)
        compose.runOnIdle { vm.acknowledge(vm.devices.value.single().acknowledgeKey) }
        show(signal)

        compose.onNodeWithText("빈 교실 기록에 있던 전파").assertDoesNotExist()
    }
}
