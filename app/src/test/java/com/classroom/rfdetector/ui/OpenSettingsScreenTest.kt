package com.classroom.rfdetector.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 설정 화면이 없는 기종을 흉내 낸다: 받아 줄 화면이 없는 요청은 실제 기기처럼 예외를 던진다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpenSettingsScreenTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    /** 앱에서처럼 화면(Activity)에서 연다. */
    private val screen = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Before fun failLikeARealDevice() { shadowOf(app).checkActivities(true) }

    private fun install(action: String) {
        val info = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "com.android.settings"; name = "Settings" } }
        @Suppress("DEPRECATION")
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(action), info)
    }

    @Test fun opensTheRequestedScreenWhenThePhoneHasIt() {
        install(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        openSettingsScreen(screen, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        assertEquals(Settings.ACTION_AIRPLANE_MODE_SETTINGS, shadowOf(screen).nextStartedActivity.action)
    }

    @Test fun fallsBackToTheMainSettingsScreenInsteadOfCrashing() {
        install(Settings.ACTION_SETTINGS)
        openSettingsScreen(screen, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        assertEquals(Settings.ACTION_SETTINGS, shadowOf(screen).nextStartedActivity.action)
    }

    @Test fun doesNothingWhenNoSettingsScreenExistsAtAll() {
        openSettingsScreen(screen, Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        assertNull(shadowOf(screen).nextStartedActivity)
    }
}
