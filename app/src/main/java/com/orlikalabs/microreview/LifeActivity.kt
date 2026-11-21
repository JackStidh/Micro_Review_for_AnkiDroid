package com.orlikalabs.microreview

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orlikalabs.microreview.ui.LauncherApp
import com.orlikalabs.microreview.ui.LifeActions
import com.orlikalabs.microreview.ui.LifeScreen
import com.orlikalabs.microreview.ui.LifeState
import com.orlikalabs.microreview.ui.aero.AeroTheme
import com.orlikalabs.microreview.ui.aero.ThemeVisit
import java.time.LocalDate
import kotlin.concurrent.thread

/** Life: the day's habits, the tasks, the rank and seals, the guard's settings ([LifeScreen]). */
class LifeActivity : ComponentActivity(), LifeActions {
    private var state by mutableStateOf(LifeState())
    private var appsLoaded by mutableIntStateOf(0)
    private var apps: List<LauncherApp>? = null
    private var loadingApps = false

    private val askSteps = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        val palette = ThemeVisit.arrive(this)
        val bars = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
        super.onCreate(savedInstanceState)
        refresh()
        val tab = intent.getIntExtra(EXTRA_TAB, 0)
        setContent { AeroTheme(palette) { LifeScreen(state, this, tab, appsLoaded) } }
    }

    override fun onStart() {
        super.onStart()
        ThemeVisit.started()
        isShown = true
        GuardStore.readSteps(this)
        refresh()
    }

    override fun onStop() {
        super.onStop()
        ThemeVisit.stopped()
        isShown = false
    }

    private fun refresh() {
        val habits = LifeStore.habits(this)
        state = LifeState(
            promptsOn = LifeSettings.promptsOn(this),
            habits = habits,
            habitNames = habits.associate { it.id to LifeText.habitName(this, it) },
            habitDays = LifeStore.habitDays(this),
            today = LocalDate.now(),
            tasks = LifeStore.tasks(this),
            xp = LifeStore.xp(this),
            ledger = LifeStore.ledger(this),
            unlocked = LifeStore.unlocked(this),
            spellMinutes = GuardStore.spellMinutes(this),
            watched = GuardStore.watched(this),
            sessionMinutes = GuardStore.sessionMinutes(this),
            inactivityOn = GuardStore.inactivityOn(this),
            canCountSteps = GuardStore.canCountSteps(this),
            hasStepSensor = GuardStore.hasStepSensor(this),
            stepsToday = GuardStore.stepsToday(this),
            bedtime = GuardStore.bedtime(this),
            sleepMs = GuardStore.sleepMs(this),
            now = System.currentTimeMillis(),
        )
    }

    override fun back() = finish()

    override fun setPromptsOn(on: Boolean) {
        LifeSettings.setPromptsOn(this, on)
        refresh()
    }

    override fun tapHabit(habit: Habits.Habit, delta: Int) {
        LifeStore.tap(this, habit, delta)
        refresh()
    }

    override fun saveHabit(original: Habits.Habit?, habit: Habits.Habit) {
        val habits = LifeStore.habits(this)
        LifeStore.setHabits(this, if (original == null) habits + habit else habits.map { if (it.id == original.id) habit else it })
        refresh()
    }

    override fun deleteHabit(habit: Habits.Habit) {
        LifeStore.setHabits(this, LifeStore.habits(this).filter { it.id != habit.id })
        refresh()
    }

    override fun addTask(title: String, due: Long?, starred: Boolean) {
        LifeStore.addTask(this, title, due, starred)
        refresh()
    }

    override fun completeTask(id: Long) {
        LifeStore.completeTask(this, id)
        refresh()
    }

    override fun reopenTask(id: Long) {
        LifeStore.updateTask(this, id) { it.copy(done = null) }
        refresh()
    }

    override fun deleteTask(id: Long) {
        LifeStore.deleteTask(this, id)
        refresh()
    }

    override fun starTask(id: Long) {
        LifeStore.updateTask(this, id) { it.copy(starred = !it.starred) }
        refresh()
    }

    override fun setSpellMinutes(minutes: Int) {
        GuardStore.setSpellMinutes(this, minutes)
        refresh()
    }

    override fun setWatched(apps: Set<String>) {
        GuardStore.setWatched(this, apps)
        refresh()
    }

    override fun apps(): List<LauncherApp>? {
        apps?.let { return it }
        if (!loadingApps) {
            loadingApps = true
            thread {
                val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val found = packageManager.queryIntentActivities(launcher, 0)
                    .map { LauncherApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
                    .filter { it.packageName != packageName }
                    .distinctBy { it.packageName }
                    .sortedBy { it.name.lowercase() }
                runOnUiThread {
                    apps = found
                    appsLoaded++
                }
            }
        }
        return null
    }

    override fun setSessionMinutes(minutes: Int) {
        GuardStore.setSessionMinutes(this, minutes)
        refresh()
    }

    override fun setInactivityOn(on: Boolean) {
        GuardStore.setInactivityOn(this, on)
        refresh()
    }

    override fun allowSteps() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) askSteps.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    override fun setBedtime(minutes: Int) {
        GuardStore.setBedtime(this, minutes)
        refresh()
    }

    companion object {
        private const val EXTRA_TAB = "tab"

        /** Shown: the monitor shows no prompt over it. */
        @Volatile
        var isShown = false
            private set

        fun intent(context: Context, tab: Int = 0) = Intent(context, LifeActivity::class.java).putExtra(EXTRA_TAB, tab)
    }
}
