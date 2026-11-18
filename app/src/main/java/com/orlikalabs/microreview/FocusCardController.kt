package com.orlikalabs.microreview

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * The full-screen card (layout `focus_review`), shown over the app in use ([CardOverlay]) or in
 * [FocusReviewActivity], in one of three [Mode]s:
 * - the app gate: a chosen app was opened without a pass. A few cards must be answered (Pass or Fail, both count)
 *   before the app shows. Leaving goes to the home screen. One emergency skip a day;
 * - a nudge: one card, then "Keep going" or "Done" (closes by itself); "Snooze" for a while;
 * - a session: cards follow each other until "Done".
 *
 * The card is on screen from the moment it is shown to its answer, so the time recorded with the answer is exact;
 * a card left unanswered doesn't count any. Under the answer, the note's fields the card didn't show ([NoteFields]).
 * Its buttons wait a second before they work ([APPEAR_LOCK_MS]): a card that pops up while the user is typing
 * mustn't take the next tap. "Bury" puts the card away until tomorrow; it doesn't count as an answer.
 *
 * Used on the main thread; AnkiDroid is queried on its own thread.
 */
class FocusCardController(private val context: Context, root: View, private val host: Host) {
    enum class Mode { GATE, NUDGE, SESSION }

    /** Where the card is shown. */
    interface Host {
        /** The card screen is done. */
        fun close()

        /** Out of the gate without its cards: away from the gated app, to the home screen. */
        fun leaveApp()
    }

    var mode = Mode.SESSION
        private set

    /** The app behind the gate. */
    var gatedApp: String? = null
        private set

    private var required = 0
    private var answered = 0
    private var card: CardInfo? = null
    private var revealed = false
    private var busy = false

    /** A nudge's card was answered: "Keep going" or "Done". */
    private var choosing = false
    private var snoozeOpen = false
    private var fields: List<Pair<String, String>> = emptyList()

    /** The first card's timer goes on rather than starting over (opened from the notification showing it). */
    private var continueTimer = false
    private var stopped = false

    /** Until then (uptime), the buttons don't work. */
    private var lockedUntil = 0L

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { if (choosing && !stopped) host.close() }
    private val unlock = Runnable { if (!stopped) render() }

    private val scroll: ScrollView = root.findViewById(R.id.focusScroll)
    private val title: TextView = root.findViewById(R.id.focusTitle)
    private val progress: TextView = root.findViewById(R.id.focusProgress)
    private val question: TextView = root.findViewById(R.id.focusQuestion)
    private val divider: View = root.findViewById(R.id.focusDivider)
    private val answer: TextView = root.findViewById(R.id.focusAnswer)
    private val fieldsView: TextView = root.findViewById(R.id.focusFields)
    private val message: TextView = root.findViewById(R.id.focusMessage)
    private val showAnswer: Button = root.findViewById(R.id.focusShowAnswer)
    private val answerButtons: View = root.findViewById(R.id.focusAnswerButtons)
    private val snoozeRow: View = root.findViewById(R.id.focusSnoozeRow)
    private val secondary1: Button = root.findViewById(R.id.focusSecondary1)
    private val secondary2: Button = root.findViewById(R.id.focusSecondary2)
    private val bury: Button = root.findViewById(R.id.focusBury)
    private val snoozeButtons: List<Button> = listOf(
        root.findViewById(R.id.focusSnooze1),
        root.findViewById(R.id.focusSnooze2),
        root.findViewById(R.id.focusSnooze3),
        root.findViewById(R.id.focusSnooze4),
    )
    private val buttons = linkedMapOf<Int, Button>(
        AnswerButtons.AGAIN to root.findViewById(R.id.focusButton1),
        AnswerButtons.HARD to root.findViewById(R.id.focusButton2),
        AnswerButtons.GOOD to root.findViewById(R.id.focusButton3),
        AnswerButtons.EASY to root.findViewById(R.id.focusButton4),
    )

    init {
        buttons.forEach { (button, view) -> view.setOnClickListener { if (!isLocked()) answer(button) } }
        showAnswer.setOnClickListener { if (!isLocked()) reveal() }
        bury.setOnClickListener { if (!isLocked()) bury() }
        snoozeButtons[0].setOnClickListener { snooze { FocusState.snooze(context, 15) } }
        snoozeButtons[1].setOnClickListener { snooze { FocusState.snooze(context, 60) } }
        snoozeButtons[2].setOnClickListener { snooze { FocusState.snooze(context, 180) } }
        snoozeButtons[3].setOnClickListener { snooze { FocusState.snoozeUntilMorning(context) } }
    }

    fun start(mode: Mode, app: String?, continueTimer: Boolean = false) {
        this.mode = mode
        gatedApp = app
        required = FocusSettings.gateCards(context)
        answered = 0
        choosing = false
        snoozeOpen = false
        card = null
        revealed = false
        this.continueTimer = continueTimer
        main.removeCallbacks(autoClose)
        lockedUntil = SystemClock.uptimeMillis() + APPEAR_LOCK_MS
        main.removeCallbacks(unlock)
        main.postAtTime(unlock, lockedUntil)
        load(sync = true)
    }

    /** The card is no longer shown: the time spent on an unanswered card doesn't count. */
    fun stop() {
        if (stopped) return
        stopped = true
        main.removeCallbacks(autoClose)
        main.removeCallbacks(unlock)
        val app = context.applicationContext
        io.execute { ReviewTimer.clear(app) }
        io.shutdown()
    }

    /** Back, button or gesture. */
    fun onBack() {
        when {
            mode == Mode.GATE && !choosing -> host.leaveApp()
            // A nudge goes away by being answered or snoozed: back offers the snooze.
            mode == Mode.NUDGE && !choosing && card != null -> {
                snoozeOpen = !snoozeOpen
                render()
            }
            else -> host.close()
        }
    }

    /** Shows the current card, checked against AnkiDroid when [sync]. */
    private fun load(sync: Boolean) {
        busy = true
        render()
        val app = context.applicationContext
        val keepTimer = continueTimer
        continueTimer = false
        io.execute {
            if (sync) {
                try {
                    CardStateController.syncWithAnkiDroid(app)
                } catch (e: Exception) {
                    FocusLog.i(app, "card check failed: $e")
                }
            }
            val next = AnkiDroidHelper(app).storedState?.takeIf { it.hasCard() }?.toCardInfo()
            if (next != null) {
                val key = CardSync.CardKey(next.noteID, next.cardOrd)
                // Seen from now on.
                if (keepTimer) ReviewTimer.onCardSeen(app, key) else ReviewTimer.restart(app, key)
            }
            main.post {
                if (stopped) return@post
                busy = false
                card = next
                revealed = false
                fields = emptyList()
                snoozeOpen = false
                // Nothing to ask: the gate is open.
                if (next == null && mode == Mode.GATE) {
                    openGate()
                } else {
                    render()
                    scroll.scrollTo(0, 0)
                }
            }
        }
    }

    private fun reveal() {
        val current = card ?: return
        revealed = true
        render()
        if (!FocusSettings.showNoteFields(context)) return
        val app = context.applicationContext
        val shown = CardText.clean(current.q) + "\n" + CardText.clean(current.a)
        io.execute {
            val extra = NoteFields.extra(app, current.noteID, shown)
            main.post {
                if (stopped || card !== current) return@post
                fields = extra
                render()
            }
        }
    }

    private fun answer(button: Int) {
        val current = card ?: return
        if (busy || !revealed) return
        busy = true
        render()
        val app = context.applicationContext
        io.execute {
            var failed = false
            val recorded = try {
                CardReview.answer(app, button, CardSync.CardKey(current.noteID, current.cardOrd), countsForSession = false) != null
            } catch (e: Exception) {
                // AnkiDroid unavailable (API disabled, permission revoked): the card stays.
                FocusLog.i(app, "answer failed: $e")
                failed = true
                false
            }
            main.post {
                if (stopped) return@post
                busy = false
                if (recorded) answered++
                when {
                    // A gate that can't record answers must not lock the app away.
                    mode == Mode.GATE && (failed || answered >= required) -> openGate()
                    mode == Mode.NUDGE && recorded -> {
                        choosing = true
                        card = null
                        render()
                        main.postDelayed(autoClose, AUTO_CLOSE_MS)
                    }
                    // The next card was stored by the answer.
                    else -> load(sync = !recorded)
                }
            }
        }
    }

    /** The cards are answered (or none is due, or the day's skip was used): the app may show until it is left. */
    private fun openGate() {
        gatedApp?.let {
            FocusSettings.setGatePass(context, it)
            FocusLog.i(context, "gate passed: $it")
        }
        host.close()
    }

    private fun isLocked(): Boolean = SystemClock.uptimeMillis() < lockedUntil

    /** Bury: the card goes away until tomorrow and the next one shows. Doesn't count toward the gate's cards. */
    private fun bury() {
        val current = card ?: return
        if (busy) return
        busy = true
        render()
        val app = context.applicationContext
        io.execute {
            val buried = try {
                CardReview.bury(app, CardSync.CardKey(current.noteID, current.cardOrd))
            } catch (e: Exception) {
                FocusLog.i(app, "bury failed: $e")
                false
            }
            main.post {
                if (stopped) return@post
                busy = false
                if (buried) FocusLog.i(app, "card buried")
                // The next card was stored by the bury.
                load(sync = !buried)
            }
        }
    }

    private fun snooze(start: () -> Unit) {
        if (isLocked()) return
        start()
        FocusLog.i(context, "snoozed from the card")
        UsageMonitorService.refreshNotification(context)
        host.close()
    }

    private fun render() {
        val current = card
        val passFail = FocusSettings.passFailOnly(context)
        title.text = when (mode) {
            Mode.GATE -> context.getString(R.string.gate_title, appLabel(context, gatedApp))
            else -> current?.deckName?.replace("::", " › ").orEmpty()
        }
        progress.text = when (mode) {
            Mode.GATE -> context.getString(R.string.gate_progress, answered.coerceAtMost(required), required)
            Mode.SESSION -> context.resources.getQuantityString(R.plurals.session_progress, answered, answered)
            Mode.NUDGE -> ""
        }
        val showCard = current != null && !choosing
        question.visibility = if (showCard) View.VISIBLE else View.GONE
        question.text = current?.let { CardText.clean(it.q) }.orEmpty()
        val showBack = showCard && revealed
        divider.visibility = if (showBack) View.VISIBLE else View.GONE
        answer.visibility = if (showBack) View.VISIBLE else View.GONE
        answer.text = current?.let { CardText.clean(it.a) }.orEmpty()
        fieldsView.visibility = if (showBack && fields.isNotEmpty()) View.VISIBLE else View.GONE
        fieldsView.text = fieldsText()
        message.visibility = if (showCard) View.GONE else View.VISIBLE
        message.text = when {
            choosing -> context.getString(R.string.nudge_recorded)
            busy -> ""
            else -> context.getString(R.string.all_cards_done)
        }
        val usable = !busy && !isLocked()
        showAnswer.visibility = if (showCard && !revealed) View.VISIBLE else View.GONE
        enable(showAnswer, usable)
        answerButtons.visibility = if (showBack) View.VISIBLE else View.GONE
        val shown = if (current != null) AnswerButtons.shownButtons(current.buttonCount, passFail) else emptyList()
        val times = current?.nextReviewTexts?.let { array -> List(array.length()) { array.optString(it) } }
        buttons.forEach { (button, view) ->
            view.visibility = if (button in shown) View.VISIBLE else View.GONE
            enable(view, usable)
            val label = context.getString(AnswerLabels.label(button, passFail))
            val next = current?.let { AnswerButtons.nextReviewText(times, button, it.buttonCount) }
            view.text = if (next == null) label else context.getString(R.string.answer_with_interval, label, next)
        }
        bury.visibility = if (showCard) View.VISIBLE else View.GONE
        enable(bury, usable)
        snoozeRow.visibility = if (snoozeOpen && showCard) View.VISIBLE else View.GONE
        snoozeButtons.forEach { enable(it, usable) }
        renderSecondary(showCard)
    }

    /** Enabled, or dimmed while it can't be used. */
    private fun enable(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    /** "Name: value" lines, the names in bold. */
    private fun fieldsText(): CharSequence {
        val text = SpannableStringBuilder()
        fields.forEachIndexed { index, (name, value) ->
            if (index > 0) text.append("\n\n")
            val start = text.length
            text.append(name)
            text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.append("\n").append(value)
        }
        return text
    }

    /** What the mode offers under the card. */
    private fun renderSecondary(showCard: Boolean) {
        secondary1.visibility = View.GONE
        secondary2.visibility = View.GONE
        when {
            mode == Mode.GATE -> {
                val today = LocalDate.now().toEpochDay()
                val left = FocusSettings.skipsLeft(context, today)
                if (left > 0) {
                    show(secondary1, context.resources.getQuantityString(R.plurals.gate_skip, left, left)) {
                        if (FocusSettings.useSkip(context, today)) {
                            FocusLog.i(context, "gate skipped")
                            openGate()
                        }
                    }
                }
                show(secondary2, context.getString(R.string.gate_leave)) { host.leaveApp() }
            }
            choosing -> {
                show(secondary1, context.getString(R.string.nudge_keep_going)) {
                    main.removeCallbacks(autoClose)
                    mode = Mode.SESSION
                    choosing = false
                    load(sync = false)
                }
                show(secondary2, context.getString(R.string.done)) { host.close() }
            }
            mode == Mode.NUDGE && showCard -> show(secondary2, context.getString(R.string.snooze)) {
                snoozeOpen = !snoozeOpen
                render()
            }
            else -> show(secondary2, context.getString(R.string.done)) { host.close() }
        }
    }

    private fun show(button: Button, text: String, onClick: () -> Unit) {
        button.text = text
        button.visibility = View.VISIBLE
        enable(button, !busy && !isLocked())
        button.setOnClickListener { if (!isLocked()) onClick() }
    }

    companion object {
        /** After a nudge's answer, "Keep going" stays this long. */
        private const val AUTO_CLOSE_MS = 8_000L

        /** A card's buttons wait this long after it appears. */
        private const val APPEAR_LOCK_MS = 1_000L
        private const val DISABLED_ALPHA = 0.4f

        /** The name of [packageName] as the launcher shows it. */
        fun appLabel(context: Context, packageName: String?): String {
            if (packageName == null) return ""
            return try {
                val manager = context.packageManager
                manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }
        }
    }
}
