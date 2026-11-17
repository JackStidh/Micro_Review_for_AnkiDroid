package com.orlikalabs.microreview

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * The full-screen card ([FocusCardController]) as a screen of the app, opened from a notification: "Keep going"
 * after a nudge's answer, or a tap on a nudge's notification to see the whole card. The gate and the nudges
 * themselves are shown over the app in use ([CardOverlay]).
 *
 * Closed when left, like the review screen of "Open after unlock".
 */
class FocusReviewActivity : ComponentActivity() {
    private lateinit var controller: FocusCardController
    private var started = false

    private val host = object : FocusCardController.Host {
        override fun close() = finish()

        override fun leaveApp() {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.focus_review)
        controller = FocusCardController(this, findViewById(R.id.focusRoot), host)
        // Back, button or gesture.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = controller.onBack()
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (started) startFromIntent()
    }

    override fun onStart() {
        super.onStart()
        isShown = true
        if (!started) {
            started = true
            startFromIntent()
        }
    }

    override fun onStop() {
        super.onStop()
        isShown = false
        controller.stop()
        // Not kept in the background.
        if (!isChangingConfigurations) finish()
    }

    private fun startFromIntent() {
        val mode = intent.getStringExtra(EXTRA_MODE)
            ?.let { name -> FocusCardController.Mode.entries.firstOrNull { it.name == name } }
            ?: FocusCardController.Mode.SESSION
        val fromNudge = intent.getBooleanExtra(EXTRA_FROM_NUDGE, false)
        if (fromNudge) {
            // The notification's card continues here, its time with it.
            NudgeSession.handOver(this)
        } else if (mode == FocusCardController.Mode.SESSION) {
            // Opened from "Keep going": that notification has done its job.
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(Notifications.NOTIFICATION_ID)
            NudgeSession.onPromptDone(this)
        }
        controller.start(mode, null, continueTimer = fromNudge)
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_FROM_NUDGE = "from_nudge"

        /** Whether the screen is in front: no nudge comes on top of it. */
        @Volatile
        var isShown = false
            private set

        /** "Keep going": cards until "Done". */
        fun sessionIntent(context: Context): Intent = intent(context, FocusCardController.Mode.SESSION)

        /** A tap on a nudge's notification: the same card, whole. */
        fun nudgeIntent(context: Context): Intent =
            intent(context, FocusCardController.Mode.NUDGE).putExtra(EXTRA_FROM_NUDGE, true)

        private fun intent(context: Context, mode: FocusCardController.Mode): Intent =
            Intent(context, FocusReviewActivity::class.java)
                .putExtra(EXTRA_MODE, mode.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
