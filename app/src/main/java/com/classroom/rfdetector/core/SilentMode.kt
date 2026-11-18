package com.classroom.rfdetector.core

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * 측정·감시 중에는 전화, 알림, 알람이 울리지 않게 방해 금지를 켜고 끝나면 되돌린다.
 * 미디어 범주만 남겨 두어 이 앱의 진동 알림은 계속 울린다. 사용자가 방해 금지 권한을 허용해야 쓸 수 있다.
 *
 * 되돌릴 원래 설정은 앱 전용 저장소에도 남긴다. 감시 중 앱을 벗어난 사이 시스템이 앱을 종료하면 메모리의 값이 사라져
 * 휴대폰이 무음으로 남기 때문이다. 다음에 앱이 시작될 때 [restoreLeftover]로 되돌린다.
 */
class SilentMode(
    context: Context,
    private val store: SharedPreferences = context.getSharedPreferences(STORE, Context.MODE_PRIVATE),
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var previous: Pair<Int, NotificationManager.Policy>? = load()
    /** 이번 프로세스에서 이미 무음으로 바꿨는지. 상태 확인이 1초마다 on()을 부르므로 한 번만 적용한다. */
    private var applied = false

    fun allowed(): Boolean = manager.isNotificationPolicyAccessGranted

    fun on() {
        if (applied || !allowed()) return
        // 앞서 종료된 감시가 남긴 원래 설정이 있으면 그것을 지킨다. 지금 상태는 그 감시가 켠 무음이다.
        if (previous == null) {
            val current = manager.currentInterruptionFilter to manager.notificationPolicy
            save(current)
            previous = current
        }
        manager.notificationPolicy = NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 0, 0)
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        applied = true
    }

    fun off() {
        applied = false
        val (filter, policy) = previous ?: return
        if (!allowed()) return
        // Android 15부터는 앱이 켠 방해 금지가 이 앱 전용 모드로 따로 관리되므로, 그 모드만 끄면 된다.
        if (Build.VERSION.SDK_INT >= 35) manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        else {
            manager.notificationPolicy = policy
            val validFilter = filter.takeIf { it in NotificationManager.INTERRUPTION_FILTER_ALL..NotificationManager.INTERRUPTION_FILTER_ALARMS }
            manager.setInterruptionFilter(validFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        previous = null
        store.edit().clear().apply()
    }

    /** 앱이 새로 시작될 때 부른다. 앞서 감시 중에 앱이 종료되어 무음이 남아 있으면 원래대로 되돌린다. */
    fun restoreLeftover() {
        if (!applied) off()
    }

    private fun save(state: Pair<Int, NotificationManager.Policy>) {
        val (filter, policy) = state
        val editor = store.edit()
            .putInt(KEY_FILTER, filter)
            .putInt(KEY_CATEGORIES, policy.priorityCategories)
            .putInt(KEY_CALLS, policy.priorityCallSenders)
            .putInt(KEY_MESSAGES, policy.priorityMessageSenders)
            .putInt(KEY_EFFECTS, policy.suppressedVisualEffects)
        if (Build.VERSION.SDK_INT >= 30) editor.putInt(KEY_CONVERSATIONS, policy.priorityConversationSenders)
        // 무음으로 바꾸기 전에 디스크에 있어야 하므로 기다린다.
        editor.commit()
    }

    private fun load(): Pair<Int, NotificationManager.Policy>? {
        if (!store.contains(KEY_FILTER)) return null
        val categories = store.getInt(KEY_CATEGORIES, 0)
        val calls = store.getInt(KEY_CALLS, 0)
        val messages = store.getInt(KEY_MESSAGES, 0)
        val effects = store.getInt(KEY_EFFECTS, 0)
        val policy = if (Build.VERSION.SDK_INT >= 30 && store.contains(KEY_CONVERSATIONS))
            NotificationManager.Policy(categories, calls, messages, effects, store.getInt(KEY_CONVERSATIONS, 0))
        else NotificationManager.Policy(categories, calls, messages, effects)
        return store.getInt(KEY_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL) to policy
    }

    private companion object {
        const val STORE = "silent-mode"
        const val KEY_FILTER = "filter"
        const val KEY_CATEGORIES = "categories"
        const val KEY_CALLS = "calls"
        const val KEY_MESSAGES = "messages"
        const val KEY_EFFECTS = "effects"
        const val KEY_CONVERSATIONS = "conversations"
    }
}
