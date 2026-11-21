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
import java.time.LocalDate

/**
 * Screenshots of every screen in every theme, with a fake AnkiDroid ([FakeAnkiDataSource]): the home screen, the
 * settings, the card (question, then answer), the gate over another app, and Move (its screen, the guided session,
 * the morning and evening prompts) with two weeks of made-up days. The theme is locked (no shuffle). Written by the shell to
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
        FocusSettings.setThemeLocked(context, true)
        MoveStore.setPhase(context, MovePlan.Phase.RECOVERY)
        // The prompts are shown by the test, not by the monitor.
        MoveStore.setPromptsOn(context, false)
        val today = LocalDate.now()
        val spots = listOf(MovePlan.PainSpot.KNEE, MovePlan.PainSpot.KNEE, MovePlan.PainSpot.THIGH, MovePlan.PainSpot.THIGH, MovePlan.PainSpot.BUTTOCK)
        val days = (13 downTo 1).map { back ->
            MovePlan.Day(
                today.minusDays(back.toLong()),
                morning = back % 4 != 0,
                evening = if (back % 5 == 0) null else "recovery",
                walkMinutes = 20 + back,
                pain = spots[(13 - back) * spots.size / 13],
                trend = MovePlan.Trend.BETTER,
            )
        } + MovePlan.Day(today, pain = MovePlan.PainSpot.BUTTOCK, walkMinutes = 10)
        context.getSharedPreferences("com.orlikalabs.microreview.move_log", Context.MODE_PRIVATE).edit()
            .putString("log", MovePlan.encode(days)).commit()
        // Life: three habits partly done, tasks, a rank, a few seals. Its prompts are shown by the test.
        LifeSettings.setPromptsOn(context, false)
        GuardStore.setBedtime(context, 23 * 60 + 30)
        val vitamin = Habits.SUGGESTIONS.first { it.id == "vitamin_d" }
        val creatine = Habits.SUGGESTIONS.first { it.id == "creatine" }
        LifeStore.setHabits(context, listOf(Habits.WATER, vitamin, creatine))
        repeat(3) { LifeStore.tap(context, Habits.WATER) }
        LifeStore.tap(context, vitamin)
        val now = System.currentTimeMillis()
        LifeStore.addTask(context, "Call the bank about the card", now - 3_600_000L, starred = true)
        LifeStore.addTask(context, "Book the physio", null)
        LifeStore.addTask(context, "Buy rice and eggs", now + 86_400_000L)
        context.getSharedPreferences("com.orlikalabs.microreview.life", Context.MODE_PRIVATE).edit()
            .putInt("xp", 2_350)
            .putStringSet("unlocked", setOf("first_step", "cards_100", "water_week", "tasks_10", "ikkyu", "shodan"))
            .commit()
        LifeStore.takeCelebrations(context)
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
                toTheBottom()
                shot("${palette.id}-3b-settings-theme")
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
            ActivityScenario.launch(MoveActivity::class.java).use {
                SystemClock.sleep(2_000)
                shot("${palette.id}-8-move")
                swipeUp()
                SystemClock.sleep(1_200)
                shot("${palette.id}-9-move-more")
            }
            ActivityScenario.launch<MoveActivity>(MoveActivity.sessionIntent(context)).use {
                SystemClock.sleep(1_500)
                shot("${palette.id}-10-session")
                tapNext()
                SystemClock.sleep(1_200)
                shot("${palette.id}-11-session-hold")
                tapCenter()
                SystemClock.sleep(3_000)
                shot("${palette.id}-12-holding")
            }
            CardOverlay.requestMove(context, MoveStore.Prompt.MORNING)
            SystemClock.sleep(1_500)
            shot("${palette.id}-13-morning")
            CardOverlay.onScreenOff(context)
            SystemClock.sleep(800)
            CardOverlay.requestMove(context, MoveStore.Prompt.EVENING)
            SystemClock.sleep(1_500)
            shot("${palette.id}-14-evening")
            CardOverlay.onScreenOff(context)
            SystemClock.sleep(800)
            for (tab in 0..3) {
                ActivityScenario.launch<LifeActivity>(LifeActivity.intent(context, tab)).use {
                    SystemClock.sleep(1_800)
                    shot("${palette.id}-15-life-$tab")
                    if (tab == 0) {
                        swipeUp()
                        SystemClock.sleep(1_000)
                        shot("${palette.id}-15-life-0b")
                    }
                }
            }
            CardOverlay.requestLife(context, LifePrompt.Spell("com.android.settings", 24, 1, session = false))
            SystemClock.sleep(1_800)
            shot("${palette.id}-16-spell")
            CardOverlay.onScreenOff(context)
            SystemClock.sleep(800)
            CardOverlay.requestLife(
                context,
                LifePrompt.Nudge(
                    listOf(Habits.Reminder(Habits.SUGGESTIONS.first { it.id == "creatine" }, 9 * 60), Habits.Reminder(Habits.WATER, null)),
                    LifeStore.tasks(context).take(1),
                    inactive = true,
                ),
            )
            SystemClock.sleep(1_800)
            shot("${palette.id}-17-nudge")
            CardOverlay.onScreenOff(context)
            SystemClock.sleep(800)
            CardOverlay.requestLife(context, LifePrompt.Bedtime(1))
            SystemClock.sleep(1_800)
            shot("${palette.id}-18-bedtime")
            CardOverlay.onScreenOff(context)
            SystemClock.sleep(800)
        }
        // The picker shuffling, one theme out (the theme shown is then any of the others).
        FocusSettings.setThemeLocked(context, false)
        FocusSettings.setThemesOff(context, setOf("mochi"))
        ActivityScenario.launch(FocusSettingsActivity::class.java).use {
            SystemClock.sleep(2_000)
            toTheBottom()
            shot("z-settings-theme-shuffle")
        }
    }

    private fun toTheBottom() {
        repeat(8) {
            swipeUp()
            SystemClock.sleep(500)
        }
        SystemClock.sleep(800)
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

    /** Move's session: "Next", the wide button bottom right. */
    private fun tapNext() {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val density = context.resources.displayMetrics.density
        shell("input tap ${bounds.width() * 2 / 3} ${bounds.height() - (72 * density).toInt()}")
    }

    /** Move's hold timer, between the exercise's panel and the buttons. */
    private fun tapCenter() {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        shell("input tap ${bounds.width() / 2} ${bounds.height() * 62 / 100}")
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    companion object {
        const val SHOTS = "/data/local/tmp/design"
    }
}
