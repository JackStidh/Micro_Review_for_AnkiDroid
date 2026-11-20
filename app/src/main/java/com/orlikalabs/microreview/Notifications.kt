package com.orlikalabs.microreview

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat


/**
 * The card as a notification: only a nudge's, when the card can't take the screen ("Display over other apps" not
 * allowed, or the take-over turned off). Never on the lock screen.
 */
class Notifications {
    companion object Factory {
        const val NOTIFICATION_ID = 1

        /** The cards offered while the phone is in use ([NudgeSession]): they pop up, and never show on the lock screen. */
        const val NUDGE_CHANNEL_ID = "nudge_review"

        /** How long "Keep going" is offered after a nudge's answer. */
        private const val KEEP_GOING_TIMEOUT_MS = 8_000L

        /** Once its answer is shown, a nudge's card is being looked at: it only goes away if forgotten. */
        private const val REVEALED_NUDGE_TIMEOUT_MS = 10 * 60_000L

        /** Channels of features that are gone (the lock screen card, the scheduled reviews): removed at start. */
        private val OLD_CHANNEL_IDS = listOf("lockscreen_review", "scheduled_review", "channel_id")
        const val ACTION_SHOW_ANSWER = "ACTION_SHOW_ANSWER"
        const val ACTION_PREVIOUS_PAGE = "ACTION_PREVIOUS_PAGE"
        const val ACTION_NEXT_PAGE = "ACTION_NEXT_PAGE"

        /** The answer buttons: followed by the button number, 1 to 4 ([AnswerButtons]), with the card ([CardReview.forCard]). */
        const val ACTION_BUTTON_PREFIX = "ACTION_BUTTON_"

        /** The height left for the card's text in the expanded view, whose maximum is 256 dp, and what it contains. */
        private const val TEXT_ROOM_DP = 120f
        private const val PAGE_BUTTONS_DP = 36f
        private const val TEXT_PADDING_DP = 17f

        /** The texts' padding in the layout (`paddingVertical`, 8 dp). */
        private const val TEXT_VERTICAL_PADDING_DP = 16f

        /** What the system's template takes from the screen's width, at most, and a margin for the question's bolder font. */
        private const val TEMPLATE_MARGINS_DP = 104f

        /**
         * The widest the notifications get, margins included: on a tablet (or a phone in landscape) they are in a
         * narrower panel than the screen (543 dp on a 753 dp wide Honor tablet), whose width SystemUI doesn't reliably
         * expose (Honor's `notification_panel_width` says "full width").
         */
        private const val MAX_PANEL_WIDTH_DP = 560
        private const val TEXT_WIDTH_MARGIN = 0.95f

        /** The pages of [card], its answer included if [revealed], at the current screen width and font size. */
        fun cardPages(context: Context, card: CardInfo, revealed: Boolean): List<CardPages.Page> {
            val density = context.resources.displayMetrics.density
            val paint = textPaint(context)
            val panelWidthDp = minOf(context.resources.configuration.screenWidthDp, MAX_PANEL_WIDTH_DP)
            val width = ((panelWidthDp - TEMPLATE_MARGINS_DP) * density * TEXT_WIDTH_MARGIN).toInt().coerceAtLeast(1)
            fun layout(text: String) = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
            val room = CardPages.Room(
                height = TEXT_ROOM_DP * density,
                pageButtons = PAGE_BUTTONS_DP * density,
                textPadding = TEXT_PADDING_DP * density,
                lineHeight = lineHeight(paint),
            )
            return CardPages.pages(CardText.clean(card.q), if (revealed) CardText.clean(card.a) else null, room) { text ->
                val laidOut = layout(text)
                if (text.isEmpty()) emptyList() else (0 until laidOut.lineCount).map { laidOut.getLineEnd(it) }
            }
        }

        /** The text of the measures: the larger of the question's and the answer's font size. */
        private fun textPaint(context: Context) = TextPaint().apply {
            textSize = maxOf(
                textSize(context, androidx.core.R.style.TextAppearance_Compat_Notification_Title),
                textSize(context, androidx.core.R.style.TextAppearance_Compat_Notification),
            )
        }

        private fun lineHeight(paint: TextPaint) = StaticLayout.Builder.obtain("Ag", 0, 2, paint, Int.MAX_VALUE).build().height.toFloat()

        /**
         * The height in pixels of a text of [lines] measured lines, with its padding; 0 for none. In pixels, not lines:
         * the question's and the answer's fonts have slightly different line heights.
         */
        private fun textHeight(context: Context, lines: Int): Int {
            if (lines == 0) return 0
            val density = context.resources.displayMetrics.density
            return kotlin.math.ceil(lines * lineHeight(textPaint(context)) + TEXT_VERTICAL_PADDING_DP * density).toInt()
        }

        private fun textSize(context: Context, style: Int): Float {
            val attributes = context.obtainStyledAttributes(style, intArrayOf(android.R.attr.textSize))
            try {
                return attributes.getDimension(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics))
            } finally {
                attributes.recycle()
            }
        }

        fun create(): Notifications = Notifications()

        /** Removes the channels of the features that are gone. Safe to call repeatedly. */
        fun removeOldChannels(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            OLD_CHANNEL_IDS.forEach { notificationManager.deleteNotificationChannel(it) }
        }

        /** The channel of the nudges: high importance, so the card pops up over the app in use. */
        fun createNudgeChannel(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NUDGE_CHANNEL_ID,
                context.getString(R.string.nudge_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.nudge_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(channel)
        }

    }

    /**
     * Posts a nudge's card ([NudgeSession]): it pops up over the app in use, never on the lock screen, and can be
     * swiped away. The answer and the answer buttons are only shown once "Show answer" was tapped
     * ([CardReview.isAnswerRevealed]).
     *
     * @param replace whether to remove the current notification first, which also closes its expanded view.
     * Pass false to update it in place (e.g. to reveal the answer).
     */
    fun showNotification(context: Context, card: CardInfo, deckName: String, isSilent: Boolean, replace: Boolean = true) {
        Log.i("Notifications", "showNotification called")
        createNudgeChannel(context)
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_collapsed)
        val title = context.getString(R.string.notification_title_deck, deckName)
        val revealed = CardReview.isAnswerRevealed(context, card)
        collapsedView.setTextViewText(R.id.textViewCollapsedHeader, CardText.clean(card.q))
        collapsedView.setTextViewText(R.id.textViewCollapsedTitle, title)

        val expandedView = cardView(context, card, revealed, PageActions(ACTION_PREVIOUS_PAGE, ACTION_NEXT_PAGE))
        expandedView.setViewVisibility(R.id.answerButtons, if (revealed) View.VISIBLE else View.GONE)
        expandedView.setViewVisibility(R.id.buttonShowAnswer, if (revealed) View.GONE else View.VISIBLE)
        expandedView.setOnClickPendingIntent(R.id.buttonShowAnswer, createIntent(context, ACTION_SHOW_ANSWER))

        setAnswerButtons(context, expandedView, card, ACTION_BUTTON_PREFIX)

        val builder = NotificationCompat.Builder(context, NUDGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(isSilent)
            .setOnlyAlertOnce(!replace)
        addNudgeActions(context, builder, card, revealed, collapsedView, expandedView)

        val notification: Notification = builder.build()

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (replace) {
            notificationManager.cancel(NOTIFICATION_ID) // Cancel the current notification before sending a new one
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * What a nudge's card adds: standard actions, which unlike the custom view's buttons also show in the pop-up, so
     * the card can be answered without opening the notification shade. Before the answer: "Show answer" and
     * "Snooze"; after it, with "Pass / Fail only": "Fail" and "Pass" (with four answers, the view's own buttons
     * stay). The collapsed view then shows the answer under the question. An untouched card times out, so that the
     * time recorded with an answer is time spent on it.
     */
    private fun addNudgeActions(
        context: Context,
        builder: NotificationCompat.Builder,
        card: CardInfo,
        revealed: Boolean,
        collapsedView: RemoteViews,
        expandedView: RemoteViews,
    ) {
        val key = CardSync.CardKey(card.noteID, card.cardOrd)
        // The pop-up shows the collapsed view: a second line, for a question longer than a word.
        collapsedView.setInt(R.id.textViewCollapsedHeader, "setMaxLines", 2)
        if (!revealed) {
            expandedView.setViewVisibility(R.id.buttonShowAnswer, View.GONE)
            builder.addAction(0, context.getString(R.string.show_answer), createIntent(context, ACTION_SHOW_ANSWER))
            builder.addAction(
                0,
                context.getString(R.string.nudge_snooze_action, FocusSettings.snoozeMinutes(context)),
                createIntent(context, NudgeSession.ACTION_SNOOZE),
            )
            builder.setTimeoutAfter(FocusSettings.NUDGE_LIFETIME_SECONDS * 1000L)
        } else {
            collapsedView.setTextViewText(R.id.textViewCollapsedTitle, CardText.clean(card.q))
            collapsedView.setTextViewText(R.id.textViewCollapsedHeader, CardText.clean(card.a))
            if (FocusSettings.passFailOnly(context)) {
                expandedView.setViewVisibility(R.id.answerButtons, View.GONE)
                builder.addAction(0, context.getString(R.string.fail), createIntent(context, ACTION_BUTTON_PREFIX + AnswerButtons.AGAIN, key))
                builder.addAction(0, context.getString(R.string.pass), createIntent(context, ACTION_BUTTON_PREFIX + AnswerButtons.GOOD, key))
            }
            builder.setTimeoutAfter(REVEALED_NUDGE_TIMEOUT_MS)
        }
        builder.setDeleteIntent(createIntent(context, NudgeSession.ACTION_DISMISSED))
        // A tap opens the whole card: the pop-up only has room for its first lines.
        builder.setContentIntent(
            PendingIntent.getActivity(
                context,
                1,
                FocusReviewActivity.nudgeIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        )
    }

    /**
     * After a nudge's answer: the same notification, updated in place, shows the answered [card] with "Keep going",
     * which opens the review screen for more cards, then disappears by itself.
     */
    fun showKeepGoingPrompt(context: Context, card: CardInfo, deckName: String) {
        createNudgeChannel(context)
        val message = context.getString(R.string.nudge_recorded)
        val title = context.getString(R.string.notification_title_deck, deckName)
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_collapsed)
        collapsedView.setTextViewText(R.id.textViewCollapsedHeader, message)
        collapsedView.setTextViewText(R.id.textViewCollapsedTitle, title)

        val expandedView = cardView(context, card, revealed = true, pageActions = null)
        expandedView.setTextViewText(R.id.textViewNextCard, message)
        expandedView.setViewVisibility(R.id.textViewNextCard, View.VISIBLE)
        expandedView.setViewVisibility(R.id.buttonShowAnswer, View.GONE)

        val keepGoing = PendingIntent.getActivity(
            context,
            0,
            FocusReviewActivity.sessionIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NUDGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(KEEP_GOING_TIMEOUT_MS)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.nudge_keep_going), keepGoing)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // No cancel first: the notification is updated in place, where the answer was tapped.
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /** Shows [card]'s answer buttons, each sending [actionPrefix] followed by its number, with the card. */
    private fun setAnswerButtons(context: Context, view: RemoteViews, card: CardInfo, actionPrefix: String) {
        // "Pass / Fail only": Again and Good under those names, Hard and Easy left out.
        val passFail = FocusSettings.passFailOnly(context)
        val visibleButtons = AnswerButtons.shownButtons(card.buttonCount, passFail)
        view.setTextViewText(R.id.button1, context.getString(AnswerLabels.label(AnswerButtons.AGAIN, passFail)))
        view.setTextViewText(R.id.button3, context.getString(AnswerLabels.label(AnswerButtons.GOOD, passFail)))
        val key = CardSync.CardKey(card.noteID, card.cardOrd)
        listOf(R.id.button1, R.id.button2, R.id.button3, R.id.button4).forEachIndexed { index, buttonId ->
            view.setOnClickPendingIntent(buttonId, createIntent(context, actionPrefix + (index + 1), key))
            view.setViewVisibility(buttonId, if (index + 1 in visibleButtons) View.VISIBLE else View.GONE)
        }
    }

    /** The broadcasts of the "Previous" and "Next" buttons. */
    private class PageActions(val previous: String, val next: String)

    /**
     * The expanded view with the current page of [card] ([CardReview.currentPage]) and, for a card of several pages,
     * "Previous" and "Next" sending [pageActions] (none if null); the caller chooses the other buttons.
     */
    private fun cardView(context: Context, card: CardInfo, revealed: Boolean, pageActions: PageActions?): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.notification_expanded_full)
        val pages = cardPages(context, card, revealed)
        val index = CardReview.currentPage(context, card, pages)
        val page = pages[index]
        view.setTextViewText(R.id.textViewExpandedHeader, page.question.orEmpty())
        view.setInt(R.id.textViewExpandedHeader, "setMinHeight", textHeight(context, page.questionLines))
        view.setViewVisibility(R.id.textViewExpandedHeader, if (page.question != null) View.VISIBLE else View.GONE)
        view.setTextViewText(R.id.textViewContent, page.answer.orEmpty())
        view.setInt(R.id.textViewContent, "setMinHeight", textHeight(context, page.answerLines))
        view.setViewVisibility(R.id.textViewContent, if (page.answer != null) View.VISIBLE else View.GONE)
        view.setViewVisibility(R.id.answerDivider, if (page.question != null && page.answer != null) View.VISIBLE else View.GONE)
        if (pageActions != null && pages.size > 1) {
            view.setViewVisibility(R.id.pageButtons, View.VISIBLE)
            view.setTextViewText(R.id.textViewPage, context.getString(R.string.card_page_count, index + 1, pages.size))
            view.setViewVisibility(R.id.buttonPreviousPage, if (index > 0) View.VISIBLE else View.INVISIBLE)
            view.setViewVisibility(R.id.buttonNextPage, if (index < pages.lastIndex) View.VISIBLE else View.INVISIBLE)
            view.setOnClickPendingIntent(R.id.buttonPreviousPage, createIntent(context, pageActions.previous))
            view.setOnClickPendingIntent(R.id.buttonNextPage, createIntent(context, pageActions.next))
        }
        return view
    }

    /** A button's broadcast; with [card] for an answer button ([CardReview.forCard]). */
    private fun createIntent(context: Context, action: String, card: CardSync.CardKey? = null): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java)
        intent.action = action
        if (card != null) CardReview.forCard(intent, card)
        // A tap must be handled right away, not wait in the background broadcast queue (busy after boot).
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

}
