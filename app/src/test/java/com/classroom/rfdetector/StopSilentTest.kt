package com.classroom.rfdetector

import android.app.NotificationManager
import android.content.ContextWrapper
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StopSilentTest {
    @get:Rule val directory = TemporaryFolder()
    private var vm: AppViewModel? = null
    @After fun close() { vm?.shutdown() }

    @Test fun stoppingAfterTheSettingsCheckIsDueRestoresTheRingerFilter() {
        val app = RuntimeEnvironment.getApplication()
        val manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        val model = AppViewModel(object : ContextWrapper(app) { override fun getFilesDir() = directory.root }).also { vm = it }
        shadowOf(Looper.getMainLooper()).idle()
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)

        model.startScan()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)
        // 설정 확인은 1초에 한 번 돈다. 마지막 확인 시각을 비워, 끝낼 때의 마지막 갱신에서 확인이 반드시 돌게 만든다.
        ReflectionHelpers.setField(model, "problemsCheckedAtMs", 0L)
        model.stopScan()

        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
    }
}
