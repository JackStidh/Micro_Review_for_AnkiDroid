# CLAUDE.md

Guidance for Claude Code when working in this repository.

## What this app is

Micro Review for AnkiDroid is a small Android app that lets the user review AnkiDroid flashcards in short bursts without opening AnkiDroid. Upstream does it from the lock screen (notification, card wallpaper) and a widget; this `focus` branch replaced those with cards that pop up while the phone is in use and a gate in front of chosen apps. It stores **no** deck or card data of its own: everything is read from and written to AnkiDroid through its public ContentProvider API, called directly: `AnkiDroidContract.kt` holds the authority, permission, URIs and columns the app uses. AnkiDroid (`com.ichi2.anki`) must be installed, with its API enabled and the `READ_WRITE_DATABASE` permission granted.

## Repository & current work

- `origin` = the developer's personal fork (push here). `upstream` = `unalkalkan/AnkiDroid-Companion`, the original repo, which we don't own: never push there or open issues there for internal tasks.
- Don't put personal information in committed files (names, accounts, emails, device models, fork URLs). Exception: the copyright lines in `LICENSE`, which name the authors.
- Upstream's **lock screen review** (`docs/lockscreen-review/PLAN.md`) was removed on `focus` by the Aero redesign: don't bring its files back there.

- Branch `focus`: nudges while the phone is in use, the app gate, Pass / Fail, snooze and Bluetooth pause. Its files
  (`Focus*.kt`, `Nudge*.kt`, `CardOverlay.kt`, `GateRules.kt`, `QuietTime.kt`, `Interruptions.kt`, `BluetoothState.kt`,
  `NoteFields*.kt`, `UsageMonitorService.kt`), how they hook into the existing ones and what is still unverified are described in
  `docs/focus/NOTES.md`, with the Aero redesign (the four themes, `ui/aero/`, `ui/HomeScreen.kt`, `ui/SettingsScreen.kt`,
  `ui/CardScreen.kt`), what it removed, and Move (exercise habits). Read it before working on that branch. The desktop Anki add-on (Python, Anki 2.1.49+, Python 3.9 syntax)
  is in `desktop/`, described in `desktop/README.md`.

## Build & run

- Toolchain: Gradle 9.3, AGP 9.0.1 (built-in Kotlin, so no `kotlin-android` plugin in `app`), Kotlin 2.3.20 with the Compose compiler Gradle plugin, Java/Kotlin bytecode target 17. Runs on JDK 17+ (CI uses 17; JDK 25 bundled with recent Android Studio works). Upgrade versions as a matched set.
- Windows: `.\gradlew.bat assembleDebug` · Unix: `./gradlew assembleDebug`
- Full build + lint + unit tests: `./gradlew build`
- Unit tests: `./gradlew test` (plain JVM, in `app/src/test`: keep testable logic free of Android classes) · Device tests: `scripts/device-tests.ps1` (VS Code task "Android: run device tests"): `app/src/androidTest` with `FakeAnkiDataSource` (installed through `AnkiData.override`), saving and restoring the app's data (`AppDataSnapshot`); on `focus` it holds `DesignPreviewTest` (screenshots of every theme, run by `.github/workflows/design-preview.yml` on the `redesign` branch, published on `design-shots`). Uses a running emulator or starts one (`-Avd`); a phone only with `-Serial`, because `am instrument` force-stops the app (the monitor stops until the app is opened again). Never use `connectedAndroidTest` on a phone (it uninstalls the app); CI uses it on a fresh emulator.
- Play Store listing texts, privacy policy and Play Console answers: `docs/play-store/` (the store asset generator is gone on `focus`).
- Release check: `scripts/release.ps1` (build + unit tests + device tests, then `gh workflow run release.yml`; a release candidate by default, `-Final` to release the validated candidate). Shared script helpers: `scripts/android-env.ps1`.
- Install on a connected device: `./gradlew installDebug`, or on Windows `scripts/install.ps1` (finds the SDK and a JDK 17+, detects the device, installs and launches; `-Wait`, `-Serial`, `-Logcat`, `-NoLaunch`). Also available in VS Code as the default build task (Ctrl+Shift+B).
- APK output: `app/build/outputs/apk/debug/app-debug.apk`
- `minSdk 26`, `targetSdk 36`, `compileSdk 36` (edge-to-edge is enforced: Compose screens handle the insets). Anything newer than API 26 must be guarded with `Build.VERSION.SDK_INT` checks.
- Manual testing needs a real device or emulator with AnkiDroid installed and at least one deck with due cards.

## Branches, versions and releases

- `develop` is the integration branch: feature branches are merged into it through pull requests (open PRs against `develop`, not `main`). `main` only receives releases.
- Version: `versionName` is `MAJOR.MINOR.PATCH`, with `-rcN` on a release candidate, in `app/build.gradle.kts`, managed by `scripts/version.py` (`show`, `patch`, `rc`, `final`, `minor`, `major`; each bump also increases `versionCode`). Don't edit the version by hand.
- `.github/workflows/integration.yml`: each PR merged into `develop` increases the **patch** digit (bot commit on `develop`); on a release candidate it adds `.N` after it instead (`0.4.0-rc1.1`).
- `.github/workflows/release.yml`: started **on request only** (`gh workflow run release.yml -f stage=rc|final`, or Actions › Release), in two stages. `rc` (default): a **release candidate** for users to validate: the next **minor** version with `-rc1` (`0.3.3` → `0.4.0-rc1`; again → `-rc2`), committed and tagged `vX.Y.0-rcN` on `develop`, the **debug** APK published as a GitHub **pre-release**; `main` is not changed. `final`: once validated, drops the suffix (`0.4.0-rc2` → `0.4.0`), merges `develop` into `main`, tags `vX.Y.0` and publishes the latest GitHub release, with the AAB signed with the upload key for the Play Store (secrets; see `docs/play-store/README.md`). Issues closed by "Closes #N" in commits are only closed when they reach `main`, i.e. at the final release.
- `.github/workflows/develop.yml`: builds (lint + unit tests) and uploads a debug APK on pushes to `develop` and on pull requests to `develop` or `main`; on pull requests it also runs the device tests on an emulator.
- `release.yml` only tags when the device tests pass on an emulator.
- `.github/workflows/pages.yml`: publishes `docs/play-store/privacy-policy.md` on GitHub Pages (the Play Console's privacy policy URL) when it changes on `develop`, or on request.
- After a merge or a release, pull `develop` before branching: the bot commits the version bump there.
- The release keystore path is git-ignored; never commit keystores or signing credentials.

## Architecture

Single module `:app`, package (namespace) and application ID `com.orlikalabs.microreview` (scripts: `$AppId` for adb, `$ClassPackage` for class names; the SharedPreferences names and broadcast actions keep their historical `com.ankidroid.companion.` prefix), mixed Java + Kotlin. The main screen is Jetpack Compose (Material 3, dynamic colors); the notification uses XML `RemoteViews` layouts.

| File | Role |
|---|---|
| `MainActivity.kt` | The home screen (`ui/HomeScreen.kt`, in the chosen Aero theme): requests the AnkiDroid and notification permissions, loads decks and due counts off the main thread, reads the monitor's status every 10 s (next card, pause, snooze, PC, answers waiting to sync) and what still has to be allowed. Card now, Snooze / Resume, Sync now, the theme. Reviewing starts as soon as a deck is selected; unselecting every deck stops it. |
| `ui/DeckTree.kt` | Builds the deck tree from `Parent::Child` names and the list of visible (unfolded) rows. |
| `ui/HomeScreen.kt` / `ui/SettingsScreen.kt` / `ui/CardScreen.kt` | The three screens, stateless Compose: home (status orb, actions, setup needs, next card, review order, deck tree, theme picker), Settings (`SettingsActions`, implemented by `FocusSettingsActivity`), and the card (`FocusCardController.Ui`) for the gate, nudges and sessions, shown by `CardOverlay` and `FocusReviewActivity`. |
| `ui/aero/` | The Aero design system: `AeroTheme` (palettes: Sakura, Yoru, Mochi, Torii Sengoku; stored in `FocusSettings.themeName`), `AeroBackground` (animated scenes), `AeroComponents` (glass panels, gel buttons, orbs, switches, dialogs), `ThemePicker`, `OverlayComposeOwner` (Compose in a `WindowManager` window). See `docs/focus/NOTES.md`. |
| `ThemeShuffle.kt` | Pure logic of the theme shuffle: the next theme among the ticked ones (never the same twice in a row), ticking (the last one stays), what counts as a new visit (`ThemeVisit` in `ui/aero/AeroTheme.kt`: back after 2 s away; a rotation or Settings and back keep the theme). Locked: always the one showing. |
| `MovePlan.kt` / `MoveStore.kt` / `MoveText.kt` / `MoveActivity.kt` / `ui/MoveScreens.kt` | Move, exercise habits (see `docs/focus/NOTES.md`): `MovePlan` the pure part (exercises, routines per phase, the day log, warnings, when a prompt is due), `MoveStore` its storage (`move` backed up, `move_log` not), `MoveText` the `strings.xml` texts, `MoveActivity` the Move screen and the guided session, the prompts shown by `CardOverlay.requestMove` from the monitor (`UsageMonitorService.moveStep`). |
| `DeckSelection.kt` | The selected decks in priority order (SharedPreferences `com.ankidroid.companion.deck_selection`). |
| `CardStateController.kt` | Single entry point for the displayed card. `showNextCard()` finds the next card in the selected decks and stores it; its notification is only posted while a nudge is shown as a notification (`NudgeSession.isActive`), never on the lock screen. `syncWithAnkiDroid()` compares the displayed card with AnkiDroid's actual next card and only acts when needed (`CardSync`). Both do ContentProvider I/O (off the main thread) and are serialized. |
| `CardSync.kt` | Pure decision of the sync: nothing, show the next card (or the first one once a deck is selected), show "Bravo", post the notification again (reboot, swiped away), or stop (no deck selected), plus the lock check throttle. |
| `BootReceiver.kt` | After a reboot or an app update, starts the monitor of the nudges and the gate again; after a reboot, also runs the sync. |
| `AnkiDataSource.kt` | The seam to AnkiDroid: `AnkiDataSource` (deck names and counts, raw due cards, due card keys, record a review, read and change the deck selected in AnkiDroid), `ContentProviderAnkiData` (the real ContentProvider calls) and `AnkiData.source()` (a fake in device tests). |
| `AnkiDroidHelper.java` | Card selection on top of `AnkiDataSource`: deck lookup and due counts, `queryNextCard(deckIds)` (first displayable due card of the first deck that has one), `queryCurrentScheduledCard(deckId, scanLimit)` (via `AnkiDroidContract.ReviewInfo`; `queryNextCard` scans each deck progressively up to its due count, max 500), `isCardDue()` (checked before answering), `reviewCard(noteId, ord, ease, timeTakenMs)` (drops AnkiDroid's study queue first, see `StudyQueue`), and persistence of the current state in SharedPreferences (`com.ichi2.anki.api.state`, JSON under `CURRENT_STATE`, including the question text). |
| `StudyQueue.kt` | Why an answer can be lost: AnkiDroid keeps the study queue of its selected deck once built and then only answers its top card ("not at top of queue", swallowed by its ContentProvider). Changing the selected deck drops the queue, so `AnkiDroidHelper.reviewCard()` selects another deck (`otherDeck()`) and the selected one again right before each answer. |
| `DeckScanner.kt` | Pure logic of the deck order: first deck with a displayable card, each deck scanned progressively (1 card, 10, then up to its due count, max 500: the query cost grows with the scan size), decks with nothing due skipped. Also `isDue()`. |
| `AnswerButtons.kt` | Which answer buttons a card shows and the ease each one sends, from the card's button count (2, 3 or 4). |
| `CardText.kt` | Turns card HTML into displayed text (strips HTML, sound and `[[type:...]]` tags) and detects sound in a question. |
| `Notifications.kt` | The nudge channel (`nudge_review`, high importance, secret on the lock screen) and the card notification (ID `1`) when a nudge can't take the screen: custom `RemoteViews` plus standard actions (they show in the pop-up), the question and **Show answer**, then the answer and the answer buttons; a long card in pages with **Previous** / **Next** (`CardPages`). Withdrawn after 30 s untouched. After the answer, "Keep going" for 8 s. `removeOldChannels()` deletes the removed features' channels. |
| `CardPages.kt` | Pure logic of the notification's pages: everything on one page if it fits, otherwise the question's pages then the answer's (a short question stays above each page of the answer). `Notifications.cardPages()` gives it the laid out lines (`StaticLayout`, at the font size and the screen width, capped at `MAX_PANEL_WIDTH_DP`: tablets show notifications in a narrower panel) and the height left for the text (`TEXT_ROOM_DP`, checked on Android 8, 12 and 14 by a device test). |
| `NotificationReceiver.kt` | Handles the nudge notification's buttons (`ACTION_SHOW_ANSWER`, `ACTION_PREVIOUS_PAGE`, `ACTION_NEXT_PAGE`, `ACTION_BUTTON_1..4`, snooze, swiped away) and the monitor's (`FocusActions`) on a background thread (`goAsync()`). |
| `CardReview.kt` | Reviewing the displayed card, from the nudge notification or the card screen: `answer()` reviews it (only if AnkiDroid still has it due, to avoid a double review) then `CardStateController.showNextCard()`; `bury()`; `revealAnswer()` / `hideAnswer()` store which card's answer is shown (prefs `com.ankidroid.companion.card_review`, keyed by card so it clears itself); `showPage()` for **Previous** / **Next**. |
| `PeriodicWorker.kt` | WorkManager job (`"WORKER_ANKI"`, hourly, `PeriodicWorker.schedule()`): runs the sync, which also finds new cards after "Bravo", and starts the monitor again if it was killed. |
| `ReviewTimer.kt` | The review time sent to AnkiDroid with an answer: starts when the card is seen (the card screen shows it, or a nudge's notification is posted), cleared when it is no longer seen unanswered (screen off, card screen closed), keyed by card (prefs `com.ankidroid.companion.review_timer`). 0 when no timer runs for the answered card. |
| `IssueReport.kt` / `ReportIssue.kt` | "Report an issue" (Settings › About): opens the GitHub issue form `.github/ISSUE_TEMPLATE/bug_report.yml` prefilled through query parameters named after its field ids (phone, Android version, app and AnkiDroid versions). `IssueReport` is the pure URL building. The repository is not in the sources: Gradle sets the `issues_repository` string from `GITHUB_REPOSITORY` in CI or the `origin` remote locally, and the entry is hidden when it's empty. |
| `CardInfo.java` / `StoredState.java` | Plain data holders. An "empty" state is represented by `noteID == -1 && cardOrd == -1`. |

### Core flow

```
CardReview.answer(button) ──► CardStateController.showNextCard ──► AnkiDroidHelper.queryNextCard(selected decks)
                                                     │                             └─ per deck, in order: first due card without sound in the question
                                                     └──► storeState ──► Notifications.showNotification (only during a notification nudge)

CardStateController.syncWithAnkiDroid: app opened, deck selection/order changed, before a nudge, reboot, hourly worker
        └─► queryNextCard ─► CardSync.decide ─► nothing / show next (or first) card / "Bravo" / post the notification again / stop
```

AnkiDroid doesn't tell other apps when a card is reviewed, edited or removed in it: that's why the displayed card is re-checked at these moments, and before each answer.

Cards are never skipped in AnkiDroid's scheduler: fetching due cards is read-only, and only `reviewCard()` records a review. Cards that can't be displayed simply stay due in AnkiDroid.

## Conventions & gotchas

- Card content: only `QUESTION_SIMPLE` / `ANSWER_PURE` are used, cleaned by `CardText`. Media is not rendered; cards with sound in the question are skipped (they stay due in AnkiDroid).
- Ease values depend on the card's button count: with 4 buttons 1 = Again, 2 = Hard, 3 = Good, 4 = Easy; with 3 buttons there is no Hard (Good = 2, Easy = 3). Always go through `AnswerButtons`. AnkiDroid's API has **no 5th ease**.
- Every answer button carries the card it was shown with (`CardReview.forCard()`, in the intent data so each card has its own PendingIntent), and `CardReview.answer()` ignores an answer for any other card and runs one answer at a time (#90): a double tap or a tap queued during the previous answer must not review the next card unseen, or the same card twice.
- AnkiDroid silently refuses to answer a card that isn't at the top of its kept study queue (#62): always answer through `AnkiDroidHelper.reviewCard()`, which drops that queue first, and don't query due cards between that and the answer. `FakeAnkiDataSource` refuses such answers too.
- A failed AnkiDroid query is not "nothing due" (#111): the due-card queries (`AnkiDataSource.dueCards` / `dueCardKeys`) throw `AnkiQueryException` when AnkiDroid can't be read (permission, provider unreachable, collection busy, storage access lost). The sync then keeps the displayed card (and posts it again if its notification disappeared), the next card after an answer stays unknown (no "Bravo"), `isCardDue()` returns null. Don't catch it into an empty list.
- AnkiDroid queries are ContentProvider I/O: keep them off the main thread (coroutines on `Dispatchers.IO` in the activity, `goAsync()` in receivers).
- PendingIntents must be `FLAG_IMMUTABLE` (targetSdk 31+). Distinct actions currently disambiguate the button intents; keep actions unique or use distinct request codes.
- Implicit broadcasts (`SCREEN_OFF`, `SCREEN_ON`, `USER_PRESENT`) **cannot** be registered in the manifest on API 26+. They need a runtime-registered receiver inside a long-lived component (for example a foreground service).
- Put user-facing strings in `res/values/strings.xml`.
- Backup: only the user's choices are backed up (`res/xml/backup_rules.xml` and `data_extraction_rules.xml` list them; the displayed card and the session state are left out). A new SharedPreferences file or picture that holds a setting must be added to both files.
- RemoteViews layouts only accept some views (no plain `View`: use `FrameLayout` for a divider); lint checks it.
- Logging uses `Log.i` with the tags `"Notifications"` and `"BackgroundService"`.
- Match the existing style: Kotlin for new Android components. The Java classes may stay Java unless they're being refactored anyway.
