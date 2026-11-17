package com.classroom.rfdetector.core

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.edit

/**
 * 측정·감시 중에는 전화, 알림, 알람이 울리지 않게 방해 금지를 켜고 끝나면 되돌린다.
 * 미디어 범주만 남겨 두어 이 앱의 진동 알림은 계속 울린다. 사용자가 방해 금지 권한을 허용해야 쓸 수 있다.
 * 켜기 전 상태는 앱 전용 저장소에 남겨, 프로세스가 죽은 뒤에도 다음 실행에서 되돌릴 수 있다.
 */
class SilentMode(context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val saved = context.getSharedPreferences("silent-mode", Context.MODE_PRIVATE)

    fun allowed(): Boolean = manager.isNotificationPolicyAccessGranted

    fun on() {
        if (saved.contains(FILTER) || !allowed()) return
        val policy = manager.notificationPolicy
        saved.edit(commit = true) {
            putInt(FILTER, manager.currentInterruptionFilter)
            if (policy != null) {
                putInt(CATEGORIES, policy.priorityCategories)
                putInt(CALLS, policy.priorityCallSenders)
                putInt(MESSAGES, policy.priorityMessageSenders)
                putInt(EFFECTS, policy.suppressedVisualEffects)
                if (Build.VERSION.SDK_INT >= 30) putInt(CONVERSATIONS, policy.priorityConversationSenders)
            }
        }
        manager.notificationPolicy = NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 0, 0)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    fun off() {
        if (!saved.contains(FILTER)) return
        val filter = saved.getInt(FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        val hasPolicy = saved.contains(CATEGORIES)
        val categories = saved.getInt(CATEGORIES, 0)
        val calls = saved.getInt(CALLS, 0)
        val messages = saved.getInt(MESSAGES, 0)
        val effects = saved.getInt(EFFECTS, 0)
        val conversations = saved.getInt(CONVERSATIONS, 0)
        saved.edit(commit = true) { clear() }
        if (!allowed()) return
        // Android 15부터는 앱이 켠 방해 금지가 이 앱 전용 모드로 따로 관리되므로, 그 모드만 끄면 된다.
        if (Build.VERSION.SDK_INT >= 35) manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        else {
            if (hasPolicy) manager.notificationPolicy =
                if (Build.VERSION.SDK_INT >= 30) NotificationManager.Policy(categories, calls, messages, effects, conversations)
                else NotificationManager.Policy(categories, calls, messages, effects)
            // 저장된 값이 올바른 필터(1~4)가 아니면 setInterruptionFilter가 예외를 던지므로 모두 허용으로 돌린다.
            manager.setInterruptionFilter(if (filter in 1..4) filter else NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    private companion object {
        const val FILTER = "filter"
        const val CATEGORIES = "categories"
        const val CALLS = "callSenders"
        const val MESSAGES = "messageSenders"
        const val EFFECTS = "visualEffects"
        const val CONVERSATIONS = "conversationSenders"
    }
}
