# Focus: cards while the phone is in use

Working notes for the `focus` branch: what it adds, how it is built, what is checked and what still has to be
checked on a real build and a real phone.

## What it adds

| Feature | Where |
|---|---|
| **Pass / Fail only**: Fail records Again, Pass records Good; Hard and Easy are never offered (card screen, pop-up notification). On by default. | `AnswerButtons.shownButtons`, `AnswerLabels`, `FocusSettings.passFailOnly` |
| **Nudges**: a card pops up every N minutes of phone use, only while the screen is on and unlocked, never on the lock screen. An interval per app (time spent in that app), a default for the rest, "never" for apps that must not be interrupted. | `UsageMonitorService`, `NudgeClock`, `NudgeSession` |
| **One card, or keep going**: after the answer the notification offers "Keep going" for 8 seconds; it opens the full-screen review for as many cards as wanted. | `Notifications.showKeepGoingPrompt`, `FocusReviewActivity` (SESSION) |
| **Honest timing**: the timer starts when the card is posted on an unlocked screen. An untouched pop-up card is withdrawn after 30 s, at screen off or when swiped, and its time is dropped. The full-screen card is exact. | `NudgeSession.withdraw`, `ReviewTimer` |
| **App gate**: opening a chosen app first asks for 5 cards (setting). Each answer counts. The pass lasts until another app is opened: no grace period. Nothing due: no gate. One emergency skip per day (setting). Back or Leave goes to the home screen. | `GateRules`, `UsageMonitorService.onForegroundChanged`, `FocusReviewActivity` (GATE) |
| **Snooze**: 30 min / 2 h / until tomorrow on the monitor's notification, "Snooze" on each card, more in the settings. A snooze doesn't open the gate unless chosen. | `FocusActions`, `QuietTime`, `FocusState` |
| **Quiet hours**: daily windows without cards. | `QuietTime`, `FocusSettings.quietHours` |
| **Bluetooth pause**: no cards and an open gate while a Bluetooth device is connected (car, headphones). Audio devices need no permission; every device needs "Nearby devices". Replaces the earlier driving detection (Android Auto, car mode, phone motion), removed after a false "driving" at home. | `BluetoothState`, `BluetoothWatcher` |
| **Also held back** during Do Not Disturb and calls. | `FocusState.pauseReason`, `Interruptions` |
| **1-second lock**: a card's buttons don't work for a second after it appears (dimmed), so a tap meant for the keyboard doesn't answer it. Not on the pop-up notification style (its buttons can't be delayed). | `FocusCardController.lockedUntil` |
| **Bury** (until tomorrow, AnkiDroid's `buried` column of `ReviewInfo`); not an answer, doesn't count for the gate. | `CardReview.bury`, `AnkiDataSource.buryCard` |
| **Next intervals** on Pass and Fail ("Pass · 4d"), from AnkiDroid's `next_review_times`, now kept with the stored card. | `AnswerButtons.nextReviewText`, `StoredState.nextReviewTimes` |
| **Pause while the PC is in use**: paired with Micro Review Desktop (`desktop/`), cards and the gate pause until 5 minutes after the PC's last input, read from an ntfy.sh topic just before a card or a gate. | `PcActivity`, `PcPairing` |
| **AnkiDroid sync**: answers given here stayed on the phone until AnkiDroid was opened (it only syncs then). AnkiDroid takes a `DO_SYNC` request from other apps but only through its screen (it shows its deck list for a moment): there is no unseen sync. So the phone only asks as a card screen closes, hidden under it (the screen stays 1.5 s under a "Syncing" veil, then fades): when answers were given, or the PC synced since; only online; or "Sync now". AnkiDroid ignores requests less than 2 min apart (answers left over go as the next card screen closes). The phone and the PC both review, so neither may show a card the other already reviewed: Micro Review Desktop posts "synced" with the cards it reviewed (`mrf-<code>-sync`, `synced <nid>:<ord>,...`), the phone reads it while in use (at most every 2 min) and as a card screen opens, and passes over those cards, and refuses an answer to one, until its next sync request (`AnkiSync.skipped`). AnkiDroid's moment in front doesn't end a gate pass. Not covered: a sync AnkiDroid takes but doesn't do (not logged in, metered network) lets those cards back. | `AnkiSync`, `AnkiSyncRules`, `PcActivity.refreshPcSync`, `AnkiDroidHelper` |
| **Settings screen** with a checklist of what still has to be allowed. Gear orb on the home screen. | `FocusSettingsActivity`, `ui/SettingsScreen.kt` |
| **Aero look**: four themes (see below), shuffled each time the app or a card opens, among the ticked ones; the padlock keeps the one showing. | `ui/aero/`, `ThemeShuffle` |
| **Move**: exercise habits in small doses (see below): a morning check-in and press-ups, an evening routine with a guided hold timer, the walk, and stand-up breaks from the PC add-on. Off until a program is picked. | `Move*.kt`, `ui/MoveScreens.kt`, `desktop/.../breaks.py` |
| **Life**: the day's habits (water by the cup, supplements at their times, any habit with a daily count), tasks, XP and ranks (10級 to 十段) and seals, the guard (scroll spell, long sessions, inactivity from the step counter, bedtime), with a Today board on the home screen. Its prompts are off until turned on. | `Habits`, `Tasks`, `Progress`, `Guard`, `Life*.kt`, `GuardStore`, `ui/LifeScreens.kt`, `ui/LifePromptScreens.kt`, `ui/TodayBoard.kt` |

## How it fits the existing app

- The card is only shown by the nudges and the gate. By default a nudge takes the screen over the app in use
  (`CardOverlay`, a Compose window); without "Display over other apps" it is a pop-up notification, posted only
  for the length of the nudge (`NudgeSession.isActive`, checked by `CardStateController.post`), never on the lock
  screen. A sync never posts the notification again.
- The pop-up uses its own high-importance channel (`nudge_review`, secret on the lock screen), isn't ongoing, and
  carries standard actions (Show answer, Snooze; then Fail, Pass) because those, unlike the custom view's buttons,
  show in the pop-up.
- `CardReview.answer()` from a nudge marks the session answered before `showNextCard()`, so the next card is stored
  but not posted; `CardStateController.cancelNotification()` leaves the answered card in place for the prompt.
- The monitor is a foreground service (`specialUse`) that polls the usage events once a second while the phone is
  in use, and does nothing while the screen is off. Opening the gate from it relies on "Display over other apps".
- The build has its own application ID (`com.orlikalabs.microreview.focus`) and a debug key kept in the repository
  (`focus-debug.keystore`), so CI builds install over each other and next to the upstream app. Uninstall or stop
  the upstream app: two apps showing the same cards make no sense.

## Removed on this branch

The upstream lock screen features did nothing with nudges and the gate, so the Aero redesign took them out: the
card notification on the lock screen and `LockSession` (one review per unlock, cards per unlock), the live
wallpaper (`CardWallpaperService`) and the lock screen picture, "Open after unlock" (`ReviewActivity`), card
rotation, reviews at set times (`ScheduledReview`), the home screen widget, the card style editor
(`WallpaperStyle`, `CardRenderer`, `PictureFilter`), the old Compose main screen, and the nudge style and nudge
lifetime settings (the full-screen card whenever it is allowed; a pop-up withdrawn after 30 s otherwise). Their
tests, the scenario device tests, `StoreAssetsSetup`, `scripts/store-assets.ps1` and the JitPack test dependency
went with them. `Notifications.removeOldChannels` deletes the channels they used.

## The Aero look

Glass and gel in the style of a Japanese phone app around 2008: every screen is Compose, in `ui/`.

| Theme | Kanji | Scene | Particles | Headings |
|---|---|---|---|---|
| Sakura (default) | 桜 | Spring sky, glossy water, a blossoming branch | Petals, lens flare | Sans |
| Yoru | 夜 | Crescent moon, glass towers with lit windows, a lake | Aurora, stars, a shooting star, fireflies | Sans |
| Mochi | 餅 | Sun, clouds, glossy green hills, dango | Bubbles, sparkles | Sans |
| Torii Sengoku | 鳥居 | Red sun, ink mountains, a stone path, a torii | Embers, gold leaf | Serif |

| File | Role |
|---|---|
| `ui/aero/AeroTheme.kt` | `AeroPalette` (colors, scene, particles, heading font), `AeroThemes` (the four, the stored choice: `FocusSettings.themeName`), `AeroTheme` (MaterialTheme from the palette), `LocalAero`, `LocalAeroMotion` (off with animations off in Developer options). |
| `ui/aero/AeroBackground.kt` | The scene behind every screen: the still part drawn once into a bitmap (`drawWithCache`), the live part each frame. Particles come from a fixed hash, not a random generator, so a frame only depends on the time. |
| `ui/aero/AeroComponents.kt` | `GlassPanel`, `GelButton` (with `charge`: not clickable until full, used by the 1-second lock), `Orb`, `AeroSwitch`, `AeroCheck`, `Beads` (gate progress), `Seal` (a red hanko in Torii), `SectionTitle`, `Pill`, `AeroDialog`, `ChoiceDialog`, `AeroText`. |
| `ui/aero/ThemePicker.kt` | The four tiles, each a still preview of its scene. |
| `ui/aero/OverlayComposeOwner.kt` | Lifecycle, saved state and view model owners for a `ComposeView` in a `WindowManager` window (the card overlay), which has no activity to provide them. |
| `ui/HomeScreen.kt` | Status orb (minutes to the next card, or why cards are held back), Card now / Snooze or Resume / Sync now, what still has to be allowed, the next card, the review order, the deck tree, the theme picker. |
| `ui/SettingsScreen.kt` | Settings in glass sections, with `SettingsActions` implemented by `FocusSettingsActivity`. |
| `ui/CardScreen.kt` | The card, for the gate, a nudge and a session (`FocusCardController.Ui`): question, Show answer, ✕ △ ◯ ◎ answer gels with the next intervals, bury, snooze, "記録" after an answer, "お疲れ様！" when nothing is left; the "Syncing" veil as it closes. Used by `CardOverlay` and `FocusReviewActivity`. |

The kanji are decoration (`translatable="false"` in `strings.xml`); every text a user reads is English beside
them. Screenshots of every theme: push to `redesign` (or run "Design preview" on a branch): `DesignPreviewTest`
takes them on an emulator and `design-preview.yml` puts them on the `design-shots` branch.


## Move

Exercise habits delivered like the cards: prompted when the phone is in use, a few minutes at a time.

- **Programs** (`MovePlan`): Recovery, for disc-related back and leg pain (McKenzie press-ups and standing extensions,
  McGill bird dog, side plank and curl-up, bridges, hip hinge practice, walking, plus posture, hip and foot work that
  loads nothing), and Rebuild (strength every other day at most, bodyweight in weeks 1 and 2, a light loaded hinge
  and carries from week 3, the recovery set as the warm-up). Picked on the Move screen; Move is off until then (a
  friend using the app gets nothing they didn't choose). Rebuild is started by hand, after a readiness note.
- **Morning**: the first unlock between 4:00 and 13:00 after the phone wasn't used for 3 hours (a glance at the lock
  screen doesn't count) shows the check-in (where the leg pain reaches, better or worse than yesterday), the
  "no bending forward for the first hour" reminder and the press-ups. **Evening**: from the chosen time (19:00 by
  default), at most 3 times, "Later" waits 45 minutes. Both in the card's window (`CardOverlay.requestMove`), held
  back by a snooze, a call, Do Not Disturb and a Bluetooth pause, not by quiet hours or the PC; never over the app
  itself or AnkiDroid, never over a card, and a nudge doesn't replace them (the gate does).
- **Guided session** (`MoveActivity`, `MoveSessionScreen`): one exercise at a time, a timer orb for the holds (a
  tick and a buzz at the end), right side then left, and a stop after each part (the rehab set alone counts).
- **Warnings** (`MovePlan.warning`): the pain back at the knee or lower three mornings running after being higher
  (back off), or worse the morning after a strength session. The rules and the emergency signs are on the screen.
- **Log**: the last 60 days in `move_log` (not backed up, like the rest of the state); the choices in `move`
  (backed up). The exercise texts are in `strings.xml` (`move_<id>_name`, `_dose`, `_how`), looked up by `MoveText`.
- **PC**: the add-on's stand-up break (`breaks.py`) after `stand_up_minutes` of sitting, with the desk exercises in
  the same order as `MovePlan.DESK` (a desktop test checks it).

Not verified on a device: the morning arming over a real night, the tone's volume, the hold orb's position for the
screenshot test's taps.

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

## Life

The lifestyle side, built like the cards: an outside nudge at the moment the phone is in use.

- **Habits** (`Habits`): water by the cup (target 8, reminded when behind an even pace from 8:00 to 21:00, at most
  every 90 min), timed doses (one tap each, reminded once when late, up to 4 h), anything else with a daily count;
  streaks; ideas to start from (vitamins, creatine, a protein shake, meals, daylight, reading...). Built-in ones are
  named in `strings.xml` (`habit_<id>`, `LifeText`) until renamed. Tap the orb to log, long-press to take one back.
- **Tasks** (`Tasks`): an optional due time from presets (in an hour, this evening, tomorrow...), a star; reminded
  when due (every 2 h until done), undated ones nudged after waiting 4 h, between 10:00 and 21:00; "Tomorrow" from a
  nudge snoozes to 9:00.
- **Progress** (`Progress`): XP per event with daily caps (a card 2, a cup 2, a task 10, the evening session 25...),
  ranks from 10級 up to 十段 (初段 at 2,200), 16 seals (`Progress.ACHIEVEMENTS`, texts `ach_<id>`); a rank up or a
  new seal is celebrated in the app (`LifeStore.takeCelebrations`, `Celebration`).
- **Guard** (`Guard`, `GuardStore`): the scroll spell (a stretch in the watched apps, by default the gated ones,
  short exits of up to 90 s included, longer than 20 min: a prompt; "5 more minutes" must be held 0, 3, 8 then 15 s;
  "On purpose" leaves the stretch alone; "Close it" goes to the home screen); a long session (60 min on the phone in
  any app, the screen off for 2 min ends one); inactivity (the step counter, `ACTIVITY_RECOGNITION`, read at each
  screen-on and every 15 min in use: under 150 steps in two hours between 9:00 and 21:00); bedtime (off until set;
  after it, a wind-down prompt; "Going to bed" within 30 min earns points); last night's sleep from the last use to
  the first (3 to 14 h).
- **Prompts** (`LifePrompts`): one at a time, in the card's window: the spell and the long session, then bedtime
  (held back only by a snooze, a call, a Bluetooth pause), Move's morning check-in, the soft nudges bundled in one
  card (due doses, water, tasks, inactivity; at most every 20 min, "Later" 30 min; also held back by Do Not
  Disturb), Move's evening routine. Off until "Nudge me" is turned on in Life (`LifeSettings`), so a new user gets
  none unasked; Move's own prompts keep their own switch.
- **Storage**: `life` backed up (habits and their days, tasks, XP, counters, seals, the guard's choices: what the user
  made and earned), `life_state` not (reminders, step readings, the bedtime answer, celebrations to show).
- **PC**: the add-on's spell breaker (`spell_minutes`, `distractions`), see `desktop/README.md`.

Not verified on a device: the step counter's first reading on registration (some phones only report on change), the
spell breaker's feel at 1-second tracking, the hold friction on a real touch screen.

