package com.orlikalabs.microreview

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * The full-screen review, in one of three [Mode]s:
 * - the app gate: a chosen app was opened without a pass. A few cards must be answered (Pass or Fail, both count)
 *   before the app shows; leaving the screen leaves the app too. One emergency skip a day;
 * - a full-screen nudge: one card, then "Keep going" or "Done";
 * - a session: "Keep going" was tapped, cards follow each other until "Done".
 *
 * The card is on screen from the moment it is shown to its answer, so the time recorded with the answer is exact;
 * a card left unanswered doesn't count any. Closed when it is left, like the review screen of "Open after unlock".
 *
 * A plain activity with a layout, not Compose: it has to open instantly over another app.
 */
class FocusReviewActivity : Activity() {
    enum class Mode { GATE, NUDGE, SESSION }

    private var mode = Mode.SESSION

    /** The app behind the gate, and the cards it asks for. */
    private var gatedApp: String? = null
    private var required = 0

    /** Cards answered since the screen was opened. */
    private var answered = 0
    private var card: CardInfo? = null
    private var revealed = false
    private var busy = false

    /** A nudge's card was answered: "Keep going" or "Done". */
    private var choosing = false

    /** AnkiDroid queries, in order, off the main thread. */
    private val io = Executors.newSingleThreadExecutor()

    private lateinit var title: TextView
    private lateinit var progress: TextView
    private lateinit var question: TextView
    private lateinit var divider: View
    private lateinit var answer: TextView
    private lateinit var message: TextView
    private lateinit var showAnswer: Button
    private lateinit var answerButtons: View
    private lateinit var secondary1: Button
    private lateinit var secondary2: Button
    private val buttons = LinkedHashMap<Int, Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.focus_review)
        title = findViewById(R.id.focusTitle)
        progress = findViewById(R.id.focusProgress)
        question = findViewById(R.id.focusQuestion)
        divider = findViewById(R.id.focusDivider)
        answer = findViewById(R.id.focusAnswer)
        message = findViewById(R.id.focusMessage)
        showAnswer = findViewById(R.id.focusShowAnswer)
        answerButtons = findViewById(R.id.focusAnswerButtons)
        secondary1 = findViewById(R.id.focusSecondary1)
        secondary2 = findViewById(R.id.focusSecondary2)
        buttons[AnswerButtons.AGAIN] = findViewById(R.id.focusButton1)
        buttons[AnswerButtons.HARD] = findViewById(R.id.focusButton2)
        buttons[AnswerButtons.GOOD] = findViewById(R.id.focusButton3)
        buttons[AnswerButtons.EASY] = findViewById(R.id.focusButton4)
        buttons.forEach { (button, view) -> view.setOnClickListener { answer(button) } }
        showAnswer.setOnClickListener {
            revealed = true
            render()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { leave() }
        }
        readIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
        if (isShown) load()
    }

    private fun readIntent(intent: Intent) {
        mode = intent.getStringExtra(EXTRA_MODE)?.let { name -> Mode.entries.firstOrNull { it.name == name } } ?: Mode.SESSION
        gatedApp = intent.getStringExtra(EXTRA_APP)
        required = FocusSettings.gateCards(this)
        answered = 0
        choosing = false
        card = null
        revealed = false
        busy = false
        if (mode == Mode.SESSION) {
            // Opened from "Keep going": that notification has done its job.
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Notifications.NOTIFICATION_ID)
            NudgeSession.onPromptDone(this)
        }
    }

    override fun onStart() {
        super.onStart()
        isShown = true
        load()
    }

    override fun onStop() {
        super.onStop()
        isShown = false
        // The card is no longer looked at: the time spent on it without an answer doesn't count.
        val context = applicationContext
        io.execute { ReviewTimer.clear(context) }
        // Not kept in the background: the gate without its cards answered gives no pass.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        leave()
    }

    /** Back: out of the gate means out of the gated app, to the home screen. */
    private fun leave() {
        if (mode == Mode.GATE) {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    /** Shows the current card, checked against AnkiDroid when the screen opens. */
    private fun load(sync: Boolean = true) {
        busy = true
        render()
        val context = applicationContext
        io.execute {
            if (sync) {
                try {
                    CardStateController.syncWithAnkiDroid(context)
                } catch (e: Exception) {
                    Log.e(TAG, "could not check the card", e)
                }
            }
            val next = AnkiDroidHelper(context).storedState?.takeIf { it.hasCard() }?.toCardInfo()
            // Seen from now on.
            if (next != null) ReviewTimer.restart(context, CardSync.CardKey(next.noteID, next.cardOrd))
            runOnUiThread {
                busy = false
                card = next
                revealed = false
                // Nothing to ask: the gate is open.
                if (next == null && mode == Mode.GATE) openGate() else render()
            }
        }
    }

    private fun answer(button: Int) {
        val current = card ?: return
        if (busy || !revealed) return
        busy = true
        render()
        val context = applicationContext
        io.execute {
            var failed = false
            val recorded = try {
                CardReview.answer(context, button, CardSync.CardKey(current.noteID, current.cardOrd), countsForSession = false) != null
            } catch (e: Exception) {
                // AnkiDroid unavailable (API disabled, permission revoked): the card stays.
                Log.e(TAG, "answering failed", e)
                failed = true
                false
            }
            runOnUiThread {
                busy = false
                if (recorded) answered++
                when {
                    // A gate that can't record answers must not lock the app away.
                    mode == Mode.GATE && (failed || answered >= required) -> openGate()
                    mode == Mode.NUDGE && recorded -> {
                        choosing = true
                        card = null
                        render()
                    }
                    // The next card was stored by the answer.
                    else -> load(sync = !recorded)
                }
            }
        }
    }

    /** The cards are answered (or none is due, or the day's skip was used): the app may show until it is left. */
    private fun openGate() {
        gatedApp?.let { FocusSettings.setGatePass(this, it) }
        finish()
    }

    private fun render() {
        val current = card
        val passFail = FocusSettings.passFailOnly(this)
        title.text = when (mode) {
            Mode.GATE -> getString(R.string.gate_title, appLabel(gatedApp))
            else -> current?.deckName?.replace("::", " › ").orEmpty()
        }
        progress.text = when (mode) {
            Mode.GATE -> getString(R.string.gate_progress, answered.coerceAtMost(required), required)
            Mode.SESSION -> resources.getQuantityString(R.plurals.session_progress, answered, answered)
            Mode.NUDGE -> ""
        }
        val showCard = current != null && !choosing
        question.visibility = if (showCard) View.VISIBLE else View.GONE
        question.text = current?.let { CardText.clean(it.q) }.orEmpty()
        divider.visibility = if (showCard && revealed) View.VISIBLE else View.GONE
        answer.visibility = if (showCard && revealed) View.VISIBLE else View.GONE
        answer.text = current?.let { CardText.clean(it.a) }.orEmpty()
        message.visibility = if (showCard) View.GONE else View.VISIBLE
        message.text = when {
            choosing -> getString(R.string.nudge_recorded)
            busy -> ""
            else -> getString(R.string.all_cards_done)
        }
        showAnswer.visibility = if (showCard && !revealed) View.VISIBLE else View.GONE
        showAnswer.isEnabled = !busy
        answerButtons.visibility = if (showCard && revealed) View.VISIBLE else View.GONE
        val shown = if (current != null) AnswerButtons.shownButtons(current.buttonCount, passFail) else emptyList()
        buttons.forEach { (button, view) ->
            view.visibility = if (button in shown) View.VISIBLE else View.GONE
            view.isEnabled = !busy
            view.setText(AnswerLabels.label(button, passFail))
        }
        renderSecondary(showCard)
    }

    /** What the mode offers under the card. */
    private fun renderSecondary(showCard: Boolean) {
        secondary1.visibility = View.GONE
        secondary2.visibility = View.GONE
        when {
            mode == Mode.GATE -> {
                val today = LocalDate.now().toEpochDay()
                val left = FocusSettings.skipsLeft(this, today)
                if (left > 0) {
                    show(secondary1, resources.getQuantityString(R.plurals.gate_skip, left, left)) {
                        if (FocusSettings.useSkip(this, today)) openGate()
                    }
                }
                show(secondary2, getString(R.string.gate_leave)) { leave() }
            }
            choosing -> {
                show(secondary1, getString(R.string.nudge_keep_going)) {
                    mode = Mode.SESSION
                    choosing = false
                    load(sync = false)
                }
                show(secondary2, getString(R.string.done)) { finish() }
            }
            mode == Mode.NUDGE && showCard -> {
                val minutes = FocusSettings.snoozeMinutes(this)
                show(secondary1, getString(R.string.nudge_snooze_action, minutes)) {
                    FocusState.snooze(this, minutes)
                    UsageMonitorService.refreshNotification(this)
                    finish()
                }
                show(secondary2, getString(R.string.not_now)) { finish() }
            }
            else -> show(secondary2, getString(R.string.done)) { finish() }
        }
    }

    private fun show(button: Button, text: String, onClick: () -> Unit) {
        button.text = text
        button.visibility = View.VISIBLE
        button.isEnabled = !busy
        button.setOnClickListener { onClick() }
    }

    /** The name of [packageName] as the launcher shows it. */
    private fun appLabel(packageName: String?): String {
        if (packageName == null) return ""
        return try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    companion object {
        private const val TAG = "Focus"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_APP = "app"

        /** Whether the screen is in front: no nudge comes on top of it. */
        @Volatile
        var isShown = false
            private set

        /** The gate in front of [app]. */
        fun gateIntent(context: Context, app: String): Intent = intent(context, Mode.GATE).putExtra(EXTRA_APP, app)

        /** A full-screen nudge. */
        fun nudgeIntent(context: Context): Intent = intent(context, Mode.NUDGE)

        /** "Keep going": cards until "Done". */
        fun sessionIntent(context: Context): Intent = intent(context, Mode.SESSION)

        private fun intent(context: Context, mode: Mode): Intent =
            Intent(context, FocusReviewActivity::class.java)
                .putExtra(EXTRA_MODE, mode.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
