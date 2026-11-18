package com.classroom.rfdetector.core

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SilentModeTest {
    private val context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val userPolicy = NotificationManager.Policy(
        NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
        NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS, NotificationManager.Policy.PRIORITY_SENDERS_STARRED)

    @Before fun userHasSoundOn() {
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.notificationPolicy = userPolicy
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @Test fun watchingSilencesThePhoneAndEndingRestoresTheUserSettings() {
        val silent = SilentMode(context)
        silent.on()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)
        assertEquals(NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, manager.notificationPolicy.priorityCategories)
        silent.off()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        assertEquals(userPolicy.priorityCategories, manager.notificationPolicy.priorityCategories)
    }

    @Test fun appKilledWhileSilencedRestoresTheUserSettingsOnNextStart() {
        SilentMode(context).on()
        // 감시 중 앱을 벗어난 사이 시스템이 앱을 종료한다. 메모리의 원래 설정은 사라진다.
        val afterRestart = SilentMode(context)
        afterRestart.restoreLeftover()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        assertEquals(userPolicy.priorityCategories, manager.notificationPolicy.priorityCategories)
        assertEquals(userPolicy.priorityCallSenders, manager.notificationPolicy.priorityCallSenders)
    }

    @Test fun watchingAgainAfterAKillDoesNotTreatTheLeftoverSilenceAsTheUserSettings() {
        SilentMode(context).on()
        // 되돌리기 전에 다시 감시를 시작해도, 끝내면 무음이 아니라 사용자의 원래 설정으로 돌아가야 한다.
        val afterRestart = SilentMode(context)
        afterRestart.on()
        afterRestart.off()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        assertEquals(userPolicy.priorityCategories, manager.notificationPolicy.priorityCategories)
    }

    @Test fun invalidSavedFilterIsRestoredAfterAccessReturns() {
        SilentMode(context).on()
        val store = context.getSharedPreferences("silent-mode", Context.MODE_PRIVATE)
        store.edit().putInt("filter", 99).commit()
        shadowOf(manager).setNotificationPolicyAccessGranted(false)
        SilentMode(context).restoreLeftover()
        assertTrue(store.contains("filter"))

        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        SilentMode(context).restoreLeftover()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        assertEquals(userPolicy.priorityCategories, manager.notificationPolicy.priorityCategories)
        assertFalse(store.contains("filter"))
    }

    @Test fun nothingIsChangedWhenThereIsNoLeftover() {
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        // 사용자가 직접 켠 방해 금지는 건드리지 않는다.
        SilentMode(context).restoreLeftover()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)
    }
}
