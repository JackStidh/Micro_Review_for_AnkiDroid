# Focus: cards while the phone is in use

Working notes for the `focus` branch: what it adds, how it is built, what is checked and what still has to be
checked on a real build and a real phone.

## What it adds

| Feature | Where |
|---|---|
| **Pass / Fail only**: Fail records Again, Pass records Good; Hard and Easy are never offered (notification, widget, review screens). On by default. | `AnswerButtons.shownButtons`, `AnswerLabels`, `FocusSettings.passFailOnly` |
| **Nudges**: a card pops up every N minutes of phone use, only while the screen is on and unlocked, never on the lock screen. An interval per app (time spent in that app), a default for the rest, "never" for apps that must not be interrupted. | `UsageMonitorService`, `NudgeClock`, `NudgeSession` |
| **One card, or keep going**: after the answer the notification offers "Keep going" for 8 seconds; it opens the full-screen review for as many cards as wanted. | `Notifications.showKeepGoingPrompt`, `FocusReviewActivity` (SESSION) |
| **Honest timing**: the timer starts when the card is posted on an unlocked screen. An untouched card is withdrawn after 30 s (setting), at screen off or when swiped, and its time is dropped. The full-screen style is exact. | `NudgeSession.withdraw`, `ReviewTimer` |
| **App gate**: opening a chosen app first asks for 5 cards (setting). Each answer counts. The pass lasts until another app is opened: no grace period. Nothing due: no gate. One emergency skip per day (setting). Back or Leave goes to the home screen. | `GateRules`, `UsageMonitorService.onForegroundChanged`, `FocusReviewActivity` (GATE) |
| **Snooze**: 30 min / 2 h / until tomorrow on the monitor's notification, "Snooze" on each card, more in the settings. A snooze doesn't open the gate unless chosen. | `FocusActions`, `QuietTime`, `FocusState` |
| **Quiet hours**: daily windows without cards. | `QuietTime`, `FocusSettings.quietHours` |
| **Bluetooth pause**: no cards, an open gate and no scheduled reviews while a Bluetooth device is connected (car, headphones). Audio devices need no permission; every device needs "Nearby devices". Replaces the earlier driving detection (Android Auto, car mode, phone motion), removed after a false "driving" at home. | `BluetoothState`, `BluetoothWatcher` |
| **Also held back** during Do Not Disturb and calls. Scheduled reviews ("at set times") respect all of the above too. | `FocusState.pauseReason`, `Interruptions` |
| **1-second lock**: a card's buttons don't work for a second after it appears (dimmed), so a tap meant for the keyboard doesn't answer it. Not on the pop-up notification style (its buttons can't be delayed). | `FocusCardController.lockedUntil` |
| **Bury** (until tomorrow, AnkiDroid's `buried` column of `ReviewInfo`); not an answer, doesn't count for the gate. | `CardReview.bury`, `AnkiDataSource.buryCard` |
| **Next intervals** on Pass and Fail ("Pass · 4d"), from AnkiDroid's `next_review_times`, now kept with the stored card. | `AnswerButtons.nextReviewText`, `StoredState.nextReviewTimes` |
| **Pause while the PC is in use**: paired with Micro Review Desktop (`desktop/`), cards and the gate pause until 5 minutes after the PC's last input, read from an ntfy.sh topic just before a card or a gate. | `PcActivity`, `PcPairing` |
| **AnkiDroid sync**: answers given here stayed on the phone until AnkiDroid was opened (it only syncs then). Only while the phone is plugged in (one sync at night rather than all day), at most every 15 min and only online: as the card overlay closes, or under a short "Syncing" cover when the plugged-in phone is unlocked with answers waiting; or "Sync now" in the settings. AnkiDroid's `DO_SYNC` request is sent; the overlay fades out over AnkiDroid's moment in front, and that moment doesn't end a gate pass. | `AnkiSync`, `AnkiSyncRules` |
| **Settings screen** with a checklist of what still has to be allowed. Bell icon on the main screen. | `FocusSettingsActivity` |

## How it fits the existing app

- With nudges on, `LockSession.mayPostNow()` only allows the card notification for the length of a nudge
  (`NudgeSession.isActive`). Everything that posts goes through it, so the notification is otherwise "hidden"
  exactly as it is while unlocked with the wallpaper. Turning nudges off clears the hidden flag and the next sync
  posts the usual notification again.
- A nudge's notification uses its own high-importance channel (`nudge_review`, secret on the lock screen), isn't
  ongoing, and carries standard actions (Show answer, Snooze; then Fail, Pass) because those, unlike the custom
  view's buttons, show in the pop-up.
- `CardReview.answer()` from a nudge marks the session answered before `showNextCard()`, so the next card is stored
  but not posted; `CardStateController.cancelNotification()` leaves the answered card in place for the prompt.
- The gate and the full-screen review answer with `countsForSession = false`, like the widget.
- The monitor is a foreground service (`specialUse`) that polls the usage events once a second while the phone is
  in use, and does nothing while the screen is off. Opening the gate from it relies on "Display over other apps".
- `FocusReviewActivity` and `FocusSettingsActivity` are plain activities with views, not Compose (see below).
- The build has its own application ID (`com.orlikalabs.microreview.focus`) and a debug key kept in the repository
  (`focus-debug.keystore`), so CI builds install over each other and next to the upstream app. Uninstall or stop
  the upstream app: two apps showing the same cards make no sense.

## What is checked, and how

No Android SDK was available where this was written (Google's Maven repository was unreachable), so no Gradle build
has run yet. Instead:

- **Unit tests**: the whole JVM suite except `DeckTreeTest` (it needs a Compose file) was compiled with the same
  Kotlin version (2.3.20) and run: 172 tests pass, including the new `QuietTimeTest`, `NudgeClockTest`,
  `GateRulesTest`, `InterruptionsTest` and the Pass / Fail cases of `AnswerButtonsTest`.
- **Type check**: every non-Compose source, old and new, compiles against the real `android.jar` (API 36), with
  hand-written stand-ins for the few AndroidX classes they use. That covers all the new code.
- **Resources**: every `R.*` reference in every source exists; every XML file parses.

Not checked until CI runs:

- The three small edits in Compose files (`MainScreen.kt`: bell icon and `onOpenFocus`; `ReviewActivity.kt`:
  Pass / Fail labels; `MainActivity.kt`).
- The manifest merge, lint (`lintDebug` runs as a separate
  job so it can't hold the APK back).
- The device tests (`androidTest`): they still expect Again / Hard / Good / Easy. Either turn "Pass / Fail only"
  off in their setup or default it to off before proposing anything upstream.

## To check on the phone

1. Setup: AnkiDroid API permission, notifications, "Usage access", "Display over other apps". For an app installed
   from a file, Android may first ask for "Allow restricted settings" (app info, three dots).
2. Nudge: set the default interval to 5 min, use the phone, wait. Pop-up with Show answer / Snooze, then Fail /
   Pass, then "Keep going". Check the review's time in AnkiDroid's card info.
3. Ignore a nudge: gone after 30 s; the same card comes back with the next nudge, answer hidden.
4. Screen off with a nudge showing: gone, nothing recorded.
5. Gate: choose an app, open it: gate within about a second. Answer 5: the app shows. Home, open again: gate again.
   Back: home screen. Emergency skip: once, then the button is gone until tomorrow.
6. Snooze from the monitor's notification and from a card; Resume.
7. Bluetooth: connect headphones or the car: no cards, gate open; disconnect: back.

Things to watch, with the fallback if they fail:

- **The gate doesn't open over the app**: background activity starts are tightened with each Android version. Fallback:
  draw the gate as an overlay window (`TYPE_APPLICATION_OVERLAY`), which the same permission allows, or detect the
  app with an accessibility service, which may start activities.
- **The app shows for a moment before the gate**: the poll is 1 s. An accessibility service gets the event at once.
- **After the gate, the wrong screen comes back**: start the gated app's launch intent before `finish()`.
- **The monitor is killed** (battery saver, vendor task killers): the hourly worker and opening the app start it
  again; set the app's battery use to "Unrestricted".
- **The pop-up's layout** (custom view plus actions) may need its paging constants (`TEXT_ROOM_DP`) adjusted.
- **A timed-out notification**: `NudgeSession.reconcile()` notices within a second while the screen is on.

## Not done yet

- Days of the week for quiet hours; a quick settings tile for snooze.
- Images and sound on cards (an upstream limit: cards are plain text, cards with sound are skipped).
- Gate statistics (how often each app was opened, cards paid).
- Translating the new texts; the device tests for the new flows.
