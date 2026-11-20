package com.orlikalabs.microreview

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orlikalabs.microreview.FakeAnkiDataSource.Card
import com.orlikalabs.microreview.ui.aero.AeroThemes
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshots of every screen in every theme, with a fake AnkiDroid ([FakeAnkiDataSource]): the home screen, the
 * settings, the card (question, then answer), and the gate over another app. Written by the shell to
 * [SHOTS] (`adb pull` it); the app's data is restored after ([AppDataSnapshot]).
 */
@RunWith(AndroidJUnit4::class)
class DesignPreviewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val anki = FakeAnkiDataSource()
        .deck(1, "日本語::Core 2k/6k", Card(101, question = "木曜日", answer = "Thursday<br>もくようび", buttonCount = 4), Card(102, question = "山", answer = "Mountain"))
        .deck(2, "日本語::Kana", Card(201, question = "ア", answer = "a"))
        .deck(3, "Geography", Card(301, question = "What is the capital of Japan?", answer = "Tokyo"))
    private lateinit var snapshot: AppDataSnapshot

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        snapshot = AppDataSnapshot.take(context)
        context.getSharedPreferences(AnkiDroidHelper.STATE_DB, Context.MODE_PRIVATE).edit().clear().commit()
        AnkiData.override = anki
        DeckSelection.save(context, listOf(1L, 3L))
        FocusSettings.setNudgesEnabled(context, true)
        FocusSettings.setGateEnabled(context, true)
        FocusSettings.setGatedApps(context, setOf("com.android.settings"))
        shell("mkdir -p $SHOTS")
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
    }

    @After
    fun tearDown() {
        AnkiData.override = null
        snapshot.restore()
    }

    @Test
    fun everyScreenInEveryTheme() {
        for (palette in AeroThemes.ALL) {
            AeroThemes.choose(context, palette)
            ActivityScenario.launch(MainActivity::class.java).use {
                SystemClock.sleep(3_000)
                shot("${palette.id}-1-home")
                swipeUp()
                SystemClock.sleep(1_200)
                shot("${palette.id}-2-home-decks")
            }
            ActivityScenario.launch(FocusSettingsActivity::class.java).use {
                SystemClock.sleep(2_000)
                shot("${palette.id}-3-settings")
            }
            ActivityScenario.launch<FocusReviewActivity>(FocusReviewActivity.sessionIntent(context)).use {
                SystemClock.sleep(2_500)
                shot("${palette.id}-4-card")
                tapNearBottom()
                SystemClock.sleep(1_500)
                shot("${palette.id}-5-answer")
            }
            CardOverlay.request(context, FocusCardController.Mode.GATE, "com.android.settings")
            SystemClock.sleep(600)
            shot("${palette.id}-6-gate-lock")
            SystemClock.sleep(2_200)
            shot("${palette.id}-7-gate")
            CardOverlay.onScreenOff(context)
            SystemClock.sleep(800)
        }
    }

    private fun shot(name: String) = shell("screencap -p $SHOTS/$name.png")

    private fun swipeUp() {
        val metrics = context.resources.displayMetrics
        val x = metrics.widthPixels / 2
        shell("input swipe $x ${metrics.heightPixels * 3 / 4} $x ${metrics.heightPixels / 6} 400")
    }

    /** Where "Show answer" is: above "Done" and the navigation bar. */
    private fun tapNearBottom() {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val density = context.resources.displayMetrics.density
        shell("input tap ${bounds.width() / 2} ${bounds.height() - (136 * density).toInt()}")
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    companion object {
        const val SHOTS = "/data/local/tmp/design"
    }
}
