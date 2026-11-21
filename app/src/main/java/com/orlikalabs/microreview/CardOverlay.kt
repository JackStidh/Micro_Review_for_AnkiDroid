package com.orlikalabs.microreview

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.orlikalabs.microreview.ui.CardScreen
import com.orlikalabs.microreview.ui.NudgeActions
import com.orlikalabs.microreview.ui.NudgeScreen
import com.orlikalabs.microreview.ui.BedtimeActions
import com.orlikalabs.microreview.ui.BedtimeScreen
import com.orlikalabs.microreview.ui.SpellActions
import com.orlikalabs.microreview.ui.SpellScreen
import com.orlikalabs.microreview.ui.MovePromptScreen
import com.orlikalabs.microreview.ui.MovePromptData
import com.orlikalabs.microreview.ui.MovePromptActions
import com.orlikalabs.microreview.ui.aero.AeroTheme
import com.orlikalabs.microreview.ui.aero.AeroThemes
import com.orlikalabs.microreview.ui.aero.OverlayComposeOwner

/**
 * The full-screen card ([FocusCardController]) drawn over the app in use, in a window of its own ("Display over
 * other apps"). Unlike an activity, it can be shown from the background at any time: Android blocks activity starts
 * from the background, and the gate only worked while the app had just been in front.
 *
 * Drawn with Compose ([CardScreen]), in the chosen theme; the window gives Compose a lifecycle of its own
 * ([OverlayComposeOwner]). While shown, it holds the audio focus, so a video playing behind it pauses. When it
 * closes after answers, or after the PC synced its own, it asks AnkiDroid to sync ([AnkiSync]): allowed while this
 * window is visible, which then stays a moment under a "Syncing" veil over AnkiDroid's moment in front, and fades
 * out. That's the only time the phone syncs by itself: AnkiDroid can't sync unseen. Used on the main thread;
 * [request], [onScreenOff] and [onForegroundChanged] may be called from any thread.
 */
object CardOverlay {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var isShowing = false
        private set

    @Volatile
    private var shownMode: FocusCardController.Mode? = null

    @Volatile
    private var shownApp: String? = null

    /** Move's prompt shown instead of a card ([requestMove]). */
    @Volatile
    private var shownMove: MoveStore.Prompt? = null

    /** A lifestyle prompt shown instead of a card ([requestLife]). */
    @Volatile
    private var shownLife: LifePrompt? = null

    private var window: View? = null
    private var controller: FocusCardController? = null
    private var audioFocus: AudioFocusRequest? = null

    /** The Compose host of each window shown, destroyed with its window. */
    private val owners = HashMap<View, OverlayComposeOwner>()

    /** The card screen is closing over AnkiDroid's sync: the veil shows. */
    private val syncing = mutableStateOf(false)

    /** Shows the card in [mode] ([app]: the gated app), from any thread. */
    fun request(context: Context, mode: FocusCardController.Mode, app: String? = null) {
        val appContext = context.applicationContext
        main.post { show(appContext, mode, app) }
    }

    /** The screen went off: what was shown is no longer seen. */
    fun onScreenOff(context: Context) {
        val appContext = context.applicationContext
        main.post { dismiss(appContext, closedByUser = false) }
    }

    /** [app] came to the front: a gate in front of another app goes away (the user left it). */
    fun onForegroundChanged(context: Context, app: String) {
        val appContext = context.applicationContext
        main.post {
            if (shownMode == FocusCardController.Mode.GATE && app != shownApp &&
                app != appContext.packageName && app !in GateRules.TRANSIENT_PACKAGES
            ) {
                FocusLog.i(appContext, "gate closed: left for $app")
                dismiss(appContext, closedByUser = false)
            }
        }
    }

    private fun show(context: Context, mode: FocusCardController.Mode, app: String?) {
        if (!FocusState.canOpenOverApps(context)) {
            FocusLog.i(context, "can't show the card: \"Display over other apps\" isn't allowed")
            return
        }
        if (isShowing) {
            // Move's or a lifestyle prompt stays unless the gate needs the screen.
            if ((shownMove != null || shownLife != null) && mode != FocusCardController.Mode.GATE) return
            if (mode == shownMode && app == shownApp) return
            // A gate isn't replaced by a nudge: it asks for cards already.
            if (shownMode == FocusCardController.Mode.GATE && mode != FocusCardController.Mode.GATE) return
            dismiss(context, closedByUser = false)
        }
        val themed = ContextThemeWrapper(context, R.style.Theme_AnkiNotifications)
        // Drawn edge to edge: the screen keeps its content out of the system bars ([CardScreen]).
        val frame = OverlayFrame(themed) { controller?.onBack() }

        val host = object : FocusCardController.Host {
            override fun close() = dismiss(context)

            override fun leaveApp() {
                FocusLog.i(context, "gate left")
                try {
                    // Allowed while this window is visible.
                    context.startActivity(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    FocusLog.i(context, "couldn't go home: $e")
                }
                dismiss(context)
            }
        }
        val card = FocusCardController(themed, host)
        // Each card screen in another theme, unless it is locked.
        val palette = AeroThemes.shuffle(context)
        syncing.value = false
        frame.addView(
            composeView(themed) { AeroTheme(palette) { CardScreen(card.ui, card, syncing.value) } },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        attachOwner(frame)
        val params = windowParams("Micro Review card")
        try {
            windowManager(context).addView(frame, params)
        } catch (e: Exception) {
            FocusLog.i(context, "can't show the card: $e")
            owners.remove(frame)?.destroy()
            return
        }
        window = frame
        controller = card
        isShowing = true
        shownMode = mode
        shownApp = app
        requestAudioFocus(context)
        FocusLog.i(context, "card shown: ${mode.name.lowercase()}${app?.let { " for $it" }.orEmpty()}")
        card.start(mode, app)
    }

    /**
     * Closes the overlay. [closedByUser]: the card screen is done (answered, snoozed, left), the window still
     * visible: the moment to ask AnkiDroid to sync, if answers wait.
     */
    private fun dismiss(context: Context, closedByUser: Boolean = true) {
        val shown = window ?: return
        val wasGate = shownMode == FocusCardController.Mode.GATE
        controller?.stop()
        controller = null
        window = null
        isShowing = false
        shownMode = null
        shownApp = null
        shownMove = null
        shownLife = null
        val syncing = closedByUser && AnkiSync.isDueAfterCards(context) && AnkiSync.request(context, "after cards")
        if (syncing) {
            // AnkiDroid shows its deck list for a moment: this window stays over it, under the veil, then fades out.
            this.syncing.value = true
            shown.isEnabled = false
            shown.animate().setStartDelay(SYNC_HOLD_MS).setDuration(SYNC_FADE_MS).alpha(0f)
                .withEndAction { remove(context, shown) }
                .start()
        } else {
            remove(context, shown)
        }
        abandonAudioFocus(context)
        // Closed without its cards, with the gated app maybe still in front: the monitor looks at it again.
        if (wasGate) UsageMonitorService.requestRecheck()
    }

    private fun remove(context: Context, view: View) {
        try {
            windowManager(context).removeViewImmediate(view)
        } catch (e: Exception) {
            // Already removed.
        }
        owners.remove(view)?.destroy()
    }

    private fun composeView(context: Context, content: @androidx.compose.runtime.Composable () -> Unit): ComposeView =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        }

    /** Gives [frame], a window's root, its Compose host. */
    private fun attachOwner(frame: View) {
        val owner = OverlayComposeOwner()
        owner.attachTo(frame)
        owners[frame] = owner
    }

    /** A full-screen window over the app in use. */
    private fun windowParams(title: String) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.OPAQUE,
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        this.title = title
    }

    /** Shows Move's [prompt] (the morning check-in, the evening routine) in the card's window, from any thread. */
    fun requestMove(context: Context, prompt: MoveStore.Prompt) {
        val appContext = context.applicationContext
        main.post { showMove(appContext, prompt) }
    }

    private fun showMove(context: Context, prompt: MoveStore.Prompt) {
        if (!FocusState.canOpenOverApps(context) || isShowing) return
        val themed = ContextThemeWrapper(context, R.style.Theme_AnkiNotifications)
        val frame = OverlayFrame(themed) {
            MoveStore.later(context, prompt)
            dismiss(context, closedByUser = false)
        }
        val today = MoveStore.today(context)
        val days = MoveStore.days(context)
        val data = MovePromptData(
            prompt = prompt,
            pain = today?.pain,
            trend = today?.trend,
            session = MoveStore.session(context),
            streak = MovePlan.streak(days, java.time.LocalDate.now()),
            warning = MoveStore.warning(context),
        )
        val actions = object : MovePromptActions {
            override fun pain(spot: MovePlan.PainSpot) {
                if (MoveStore.today(context)?.pain == null) LifeStore.award(context, Progress.Event.CHECK_IN)
                MoveStore.updateToday(context) { it.copy(pain = spot) }
            }

            override fun trend(trend: MovePlan.Trend) = MoveStore.updateToday(context) { it.copy(trend = trend) }

            override fun pressUpsDone() {
                if (MoveStore.today(context)?.morning != true) LifeStore.award(context, Progress.Event.MORNING)
                MoveStore.updateToday(context) { it.copy(morning = true) }
                dismiss(context, closedByUser = false)
            }

            override fun start() {
                try {
                    // Allowed while this window is visible.
                    context.startActivity(MoveActivity.sessionIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    FocusLog.i(context, "couldn't open Move: $e")
                }
                dismiss(context, closedByUser = false)
            }

            override fun later() {
                MoveStore.later(context, prompt)
                dismiss(context, closedByUser = false)
            }

            override fun notToday() {
                MoveStore.setAside(context, prompt)
                dismiss(context, closedByUser = false)
            }
        }
        val palette = AeroThemes.shuffle(context)
        frame.addView(
            composeView(themed) { AeroTheme(palette) { MovePromptScreen(data, actions) } },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        attachOwner(frame)
        try {
            windowManager(context).addView(frame, windowParams("Micro Review move"))
        } catch (e: Exception) {
            FocusLog.i(context, "can't show the move prompt: $e")
            owners.remove(frame)?.destroy()
            return
        }
        window = frame
        isShowing = true
        shownMode = null
        shownApp = null
        shownMove = prompt
        FocusLog.i(context, "move prompt shown: ${prompt.name.lowercase()}")
    }

    /** Shows a lifestyle prompt ([LifePrompt]) in the card's window, from any thread. */
    fun requestLife(context: Context, prompt: LifePrompt) {
        if (prompt is LifePrompt.Move) return requestMove(context, prompt.prompt)
        val appContext = context.applicationContext
        main.post { showLife(appContext, prompt) }
    }

    private fun showLife(context: Context, prompt: LifePrompt) {
        if (!FocusState.canOpenOverApps(context) || isShowing) return
        val themed = ContextThemeWrapper(context, R.style.Theme_AnkiNotifications)
        fun close() = dismiss(context, closedByUser = false)
        fun goHome() {
            try {
                // Allowed while this window is visible: out of the app that held the attention.
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                FocusLog.i(context, "couldn't go home: $e")
            }
        }
        val frame = OverlayFrame(themed) {
            // Back pressed: as "later" for the soft ones; the spell and bedtime ask for an answer.
            if (prompt is LifePrompt.Nudge) {
                GuardStore.softLater(context)
                close()
            }
        }
        val palette = AeroThemes.shuffle(context)
        val content: @androidx.compose.runtime.Composable () -> Unit = when (prompt) {
            is LifePrompt.Spell -> {
                val label = prompt.app?.let { FocusCardController.appLabel(context, it) }
                val desk = MovePlan.deskMove((System.currentTimeMillis() / 60_000L).toInt())
                val actions = object : SpellActions {
                    override fun close() {
                        if (prompt.session) GuardStore.sessionBreak(context) else GuardStore.broken(context)
                        goHome()
                        close()
                    }

                    override fun more() {
                        if (prompt.session) GuardStore.sessionLater(context) else GuardStore.more()
                        close()
                    }

                    override fun onPurpose() {
                        GuardStore.exempt()
                        close()
                    }

                    override fun water() {
                        LifeStore.tap(context, LifeStore.habits(context).firstOrNull { it.id == Habits.WATER.id } ?: Habits.WATER)
                        close()
                    }
                }
                ({ SpellScreen(prompt, label, desk, Guard.holdMs(prompt.strikes), actions) })
            }
            is LifePrompt.Bedtime -> {
                val bedtime = GuardStore.bedtime(context)
                val actions = object : BedtimeActions {
                    override fun goingToBed() {
                        GuardStore.goingToBed(context)
                        goHome()
                        close()
                    }

                    override fun later() {
                        GuardStore.bedtimeLater(context)
                        close()
                    }

                    override fun notTonight() {
                        GuardStore.goingToBed(context, award = false)
                        close()
                    }
                }
                ({ BedtimeScreen(prompt, "%d:%02d".format(bedtime / 60, bedtime % 60), Guard.holdMs(prompt.strikes), actions) })
            }
            is LifePrompt.Nudge -> {
                val actions = object : NudgeActions {
                    override fun habit(reminder: Habits.Reminder) = LifeStore.tap(context, reminder.habit)

                    override fun taskDone(task: Tasks.Task) = LifeStore.completeTask(context, task.id)

                    override fun taskTomorrow(task: Tasks.Task) {
                        val tomorrow = java.time.LocalDate.now().plusDays(1).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        LifeStore.updateTask(context, task.id) { it.copy(snoozedUntil = tomorrow) }
                    }

                    override fun walked() {
                        LifeStore.award(context, Progress.Event.STAND_UP, Progress.Counter.STAND_UPS)
                    }

                    override fun allGood() = close()

                    override fun later() {
                        GuardStore.softLater(context)
                        close()
                    }
                }
                ({
                    NudgeScreen(
                        prompt,
                        habitName = { LifeText.habitName(context, it) },
                        habitCount = { habit -> LifeStore.habitToday(context)?.count(habit.id) ?: 0 },
                        stepsToday = GuardStore.stepsToday(context),
                        actions = actions,
                    )
                })
            }
            is LifePrompt.Move -> return
        }
        frame.addView(
            composeView(themed) { AeroTheme(palette) { content() } },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        attachOwner(frame)
        try {
            windowManager(context).addView(frame, windowParams("Micro Review life"))
        } catch (e: Exception) {
            FocusLog.i(context, "can't show the prompt: $e")
            owners.remove(frame)?.destroy()
            return
        }
        window = frame
        isShowing = true
        shownMode = null
        shownApp = null
        shownMove = null
        shownLife = prompt
        FocusLog.i(context, "life prompt shown: ${prompt.javaClass.simpleName}")
    }

    /** How long the overlay stays over AnkiDroid's moment in front after a sync request, then fades. */
    private const val SYNC_HOLD_MS = 1_500L
    private const val SYNC_FADE_MS = 300L

    private fun windowManager(context: Context) = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private fun requestAudioFocus(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        try {
            audio.requestAudioFocus(request)
            audioFocus = request
        } catch (e: Exception) {
            // Not essential.
        }
    }

    private fun abandonAudioFocus(context: Context) {
        val request = audioFocus ?: return
        audioFocus = null
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(request)
    }

    /** The overlay's root: passes back, button or gesture, to [onBack]. */
    @SuppressLint("ViewConstructor")
    private class OverlayFrame(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        private var backCallback: Any? = null

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val callback = OnBackInvokedCallback { onBack() }
                findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
                backCallback = callback
            }
        }

        override fun onDetachedFromWindow() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                (backCallback as? OnBackInvokedCallback)?.let { findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(it) }
            }
            backCallback = null
            super.onDetachedFromWindow()
        }
    }
}
