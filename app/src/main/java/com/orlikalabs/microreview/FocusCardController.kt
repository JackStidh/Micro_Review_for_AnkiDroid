package com.orlikalabs.microreview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * The full-screen card ([com.orlikalabs.microreview.ui.CardScreen]), shown over the app in use ([CardOverlay]) or in
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
 * What the screen shows is [ui], Compose state; the screen calls the actions. Used on the main thread; AnkiDroid is
 * queried on its own thread.
 */
class FocusCardController(private val context: Context, private val host: Host) {
    enum class Mode { GATE, NUDGE, SESSION }

    /** Where the card is shown. */
    interface Host {
        /** The card screen is done. */
        fun close()

        /** Out of the gate without its cards: away from the gated app, to the home screen. */
        fun leaveApp()
    }

    /** What the card screen is showing. */
    enum class Stage {
        /** Looking for the card. */
        LOADING,

        /** A card, its question, then its answer. */
        CARD,

        /** A nudge's card was answered: "Keep going" or "Done". */
        RECORDED,

        /** Nothing left to review. */
        ALL_DONE,
    }

    /** An answer button: one of [AnswerButtons], its label and the interval it gives ("4d"), if known. */
    data class Choice(val button: Int, val label: String, val interval: String?)

    /** Everything the card screen shows. */
    data class Ui(
        val mode: Mode = Mode.SESSION,
        val stage: Stage = Stage.LOADING,
        /** The gated app's name, in a gate. */
        val gatedApp: String = "",
        /** The card's deck, "Parent › Child". */
        val deck: String = "",
        val answered: Int = 0,
        /** The gate's number of cards. */
        val required: Int = 0,
        val question: String = "",
        val answer: String = "",
        val revealed: Boolean = false,
        val fields: List<Pair<String, String>> = emptyList(),
        val choices: List<Choice> = emptyList(),
        /** Something is being recorded: nothing can be tapped. */
        val busy: Boolean = true,
        /** The first second: the buttons fill up and can't be tapped yet. */
        val locked: Boolean = true,
        /** Changes each time the card screen starts: the buttons' filling starts over. */
        val lockSerial: Int = 0,
        /** Changes with each new card: the card's entrance plays again. */
        val cardSerial: Int = 0,
        val snoozeOpen: Boolean = false,
        /** The gate's emergency skips left today. */
        val skipsLeft: Int = 0,
    ) {
        val usable: Boolean get() = !busy && !locked
    }

    /** The snooze lengths offered on the card. */
    enum class Snooze { MINUTES_15, HOUR_1, HOURS_3, UNTIL_TOMORROW }

    var ui by mutableStateOf(Ui())
        private set

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
    private var lockSerial = 0
    private var cardSerial = 0

    /** The first card's timer goes on rather than starting over (opened from the notification showing it). */
    private var continueTimer = false
    private var stopped = false

    /** Until then (uptime), the buttons don't work. */
    private var lockedUntil = 0L

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val autoClose = Runnable { if (choosing && !stopped) host.close() }
    private val unlock = Runnable { if (!stopped) render() }

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
        lockSerial++
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
            mode == Mode.NUDGE && !choosing && card != null -> toggleSnooze()
            else -> host.close()
        }
    }

    // What the screen can do

    fun reveal() {
        val current = card ?: return
        if (isLocked() || busy) return
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

    fun answer(button: Int) {
        val current = card ?: return
        if (busy || !revealed || isLocked()) return
        busy = true
        render()
        val app = context.applicationContext
        io.execute {
            var failed = false
            val recorded = try {
                CardReview.answer(app, button, CardSync.CardKey(current.noteID, current.cardOrd), fromNotification = false)
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

    /** Bury: the card goes away until tomorrow and the next one shows. Doesn't count toward the gate's cards. */
    fun bury() {
        val current = card ?: return
        if (busy || isLocked()) return
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

    fun toggleSnooze() {
        snoozeOpen = !snoozeOpen
        render()
    }

    fun snooze(length: Snooze) {
        if (isLocked()) return
        when (length) {
            Snooze.MINUTES_15 -> FocusState.snooze(context, 15)
            Snooze.HOUR_1 -> FocusState.snooze(context, 60)
            Snooze.HOURS_3 -> FocusState.snooze(context, 180)
            Snooze.UNTIL_TOMORROW -> FocusState.snoozeUntilMorning(context)
        }
        FocusLog.i(context, "snoozed from the card")
        UsageMonitorService.refreshNotification(context)
        host.close()
    }

    /** The gate's emergency skip, once a day. */
    fun skip() {
        if (isLocked()) return
        val today = LocalDate.now().toEpochDay()
        if (FocusSettings.useSkip(context, today)) {
            FocusLog.i(context, "gate skipped")
            openGate()
        }
    }

    fun leave() {
        if (!isLocked()) host.leaveApp()
    }

    /** After a nudge's card: more cards, until "Done". */
    fun keepGoing() {
        main.removeCallbacks(autoClose)
        mode = Mode.SESSION
        choosing = false
        load(sync = false)
    }

    fun done() = host.close()

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
                cardSerial++
                revealed = false
                fields = emptyList()
                snoozeOpen = false
                // Nothing to ask: the gate is open.
                if (next == null && mode == Mode.GATE) openGate() else render()
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

    private fun render() {
        val current = card
        val passFail = FocusSettings.passFailOnly(context)
        val times = current?.nextReviewTexts?.let { array -> List(array.length()) { array.optString(it) } }
        ui = Ui(
            mode = mode,
            stage = when {
                choosing -> Stage.RECORDED
                current != null -> Stage.CARD
                busy -> Stage.LOADING
                else -> Stage.ALL_DONE
            },
            gatedApp = appLabel(context, gatedApp),
            deck = current?.deckName?.replace("::", " › ").orEmpty(),
            answered = answered,
            required = required,
            question = current?.let { CardText.clean(it.q) }.orEmpty(),
            answer = current?.let { CardText.clean(it.a) }.orEmpty(),
            revealed = revealed,
            fields = fields,
            choices = current?.let { card ->
                AnswerButtons.shownButtons(card.buttonCount, passFail).map { button ->
                    Choice(button, context.getString(AnswerLabels.label(button, passFail)), AnswerButtons.nextReviewText(times, button, card.buttonCount))
                }
            }.orEmpty(),
            busy = busy,
            locked = isLocked(),
            lockSerial = lockSerial,
            cardSerial = cardSerial,
            snoozeOpen = snoozeOpen,
            skipsLeft = if (mode == Mode.GATE) FocusSettings.skipsLeft(context, LocalDate.now().toEpochDay()) else 0,
        )
    }

    companion object {
        /** After a nudge's answer, "Keep going" stays this long. */
        private const val AUTO_CLOSE_MS = 8_000L

        /** A card's buttons wait this long after it appears. */
        const val APPEAR_LOCK_MS = 1_000L

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
