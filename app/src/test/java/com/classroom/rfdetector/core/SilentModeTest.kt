package com.classroom.rfdetector.core

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.content.ContextWrapper
import com.classroom.rfdetector.AppViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SilentModeTest {
    @get:Rule val directory = TemporaryFolder()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val original = Policy(
        Policy.PRIORITY_CATEGORY_ALARMS or Policy.PRIORITY_CATEGORY_CALLS,
        Policy.PRIORITY_SENDERS_STARRED, Policy.PRIORITY_SENDERS_CONTACTS, Policy.SUPPRESSED_EFFECT_PEEK,
    )

    @Before fun grantAndSetOriginal() {
        shadowOf(manager).setNotificationPolicyAccessGranted(true)
        manager.notificationPolicy = original
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    private fun assertOriginal() {
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
        val policy = manager.notificationPolicy
        assertEquals(original.priorityCategories, policy.priorityCategories)
        assertEquals(original.priorityCallSenders, policy.priorityCallSenders)
        assertEquals(original.priorityMessageSenders, policy.priorityMessageSenders)
        assertEquals(original.suppressedVisualEffects, policy.suppressedVisualEffects)
    }

    @Test fun aNewInstanceRestoresWhatTheKilledProcessLeft() {
        SilentMode(context).on()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, manager.currentInterruptionFilter)
        assertEquals(Policy.PRIORITY_CATEGORY_MEDIA, manager.notificationPolicy.priorityCategories)
        SilentMode(context).off()
        assertOriginal()
    }

    @Test fun aLeftoverRecordIsNotOverwrittenByTheSilencedState() {
        SilentMode(context).on()
        val second = SilentMode(context)
        second.on()
        second.off()
        assertOriginal()
    }

    @Test @Config(sdk = [35]) fun onAndroid15OnlyTheFilterIsTurnedOff() {
        SilentMode(context).on()
        SilentMode(context).off()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, manager.currentInterruptionFilter)
    }

    @Test fun startingTheViewModelRestoresWhatTheKilledProcessLeft() {
        SilentMode(context).on()
        val vm = AppViewModel(object : ContextWrapper(context) { override fun getFilesDir() = directory.root })
        try { assertOriginal() } finally { vm.shutdown() }
    }
}
