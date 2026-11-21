package com.orlikalabs.microreview

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orlikalabs.microreview.ui.MoveActions
import com.orlikalabs.microreview.ui.MoveScreen
import com.orlikalabs.microreview.ui.MoveSessionScreen
import com.orlikalabs.microreview.ui.MoveState
import com.orlikalabs.microreview.ui.aero.AeroTheme
import com.orlikalabs.microreview.ui.aero.ThemeVisit
import java.time.LocalDate

/**
 * Move ([MovePlan]): today's habits, the last two weeks, the phase, the routine, the rules, the prompts; and the
 * guided evening session. Opened from the home screen, and from the evening prompt straight into the session.
 */
class MoveActivity : ComponentActivity(), MoveActions {
    private var state by mutableStateOf(MoveState())

    /** The session being done, or null for the Move screen. */
    private var session by mutableStateOf<MovePlan.Session?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val palette = ThemeVisit.arrive(this)
        val bars = if (palette.isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(bars, bars)
        super.onCreate(savedInstanceState)
        state = read()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_SESSION, false)) session = MoveStore.session(this)
        savedInstanceState?.getString(STATE_SESSION)?.let { name -> session = MovePlan.Session.entries.firstOrNull { it.name == name } }
        setContent {
            AeroTheme(palette) {
                val running = session
                if (running == null) {
                    MoveScreen(state, this)
                } else {
                    MoveSessionScreen(
                        parts = MovePlan.parts(running, MoveStore.rebuildWeek(this)),
                        onFinish = { partsDone -> finishSession(running, partsDone) },
                        onClose = { session = null },
                    )
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (session != null) session = null else finish()
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        session?.let { outState.putString(STATE_SESSION, it.name) }
    }

    override fun onStart() {
        super.onStart()
        ThemeVisit.started()
        isShown = true
        state = read()
    }

    override fun onStop() {
        super.onStop()
        ThemeVisit.stopped()
        isShown = false
    }

    private fun read(): MoveState {
        val days = MoveStore.days(this)
        val today = LocalDate.now()
        return MoveState(
            phase = MoveStore.phase(this),
            days = days,
            todayDate = today,
            session = MoveStore.session(this),
            rebuildWeek = MoveStore.rebuildWeek(this),
            streak = MovePlan.streak(days, today),
            warning = MovePlan.warning(days, today),
            promptsOn = MoveStore.promptsOn(this),
            eveningMinutes = MoveStore.eveningMinutes(this),
        )
    }

    /** The parts done: all of them counts as the session, fewer as what was done ("rehab": the first part only). */
    private fun finishSession(done: MovePlan.Session, partsDone: Int) {
        val parts = MovePlan.parts(done, MoveStore.rebuildWeek(this))
        val label = if (partsDone >= parts.size) done.name.lowercase() else parts[(partsDone - 1).coerceAtLeast(0)].id
        // A strength session counts as one once its strength part is done.
        val strength = done == MovePlan.Session.STRENGTH && parts.take(partsDone).any { it.id == "strength" }
        MoveStore.updateToday(this) { it.copy(evening = label, strength = it.strength || strength) }
        FocusLog.i(this, "move session done: $label")
        session = null
        state = read()
    }

    override fun back() = finish()

    override fun pickPhase(phase: MovePlan.Phase) {
        MoveStore.setPhase(this, phase)
        state = read()
    }

    override fun pain(spot: MovePlan.PainSpot) {
        MoveStore.updateToday(this) { it.copy(pain = spot) }
        state = read()
    }

    override fun trend(trend: MovePlan.Trend) {
        MoveStore.updateToday(this) { it.copy(trend = trend) }
        state = read()
    }

    override fun morningDone() {
        MoveStore.updateToday(this) { it.copy(morning = true) }
        state = read()
    }

    override fun startSession() {
        session = MoveStore.session(this)
    }

    override fun addWalk(minutes: Int) {
        MoveStore.updateToday(this) { it.copy(walkMinutes = it.walkMinutes + minutes) }
        state = read()
    }

    override fun setPromptsOn(on: Boolean) {
        MoveStore.setPromptsOn(this, on)
        state = read()
    }

    override fun setEveningMinutes(minutes: Int) {
        MoveStore.setEveningMinutes(this, minutes)
        state = read()
    }

    companion object {
        private const val EXTRA_SESSION = "session"
        private const val STATE_SESSION = "session"

        /** Shown: the monitor shows no prompt over it. */
        @Volatile
        var isShown = false
            private set

        fun intent(context: Context) = Intent(context, MoveActivity::class.java)

        /** Straight into today's session. */
        fun sessionIntent(context: Context) = intent(context).putExtra(EXTRA_SESSION, true)
    }
}
