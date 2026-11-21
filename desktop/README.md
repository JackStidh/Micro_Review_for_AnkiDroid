# Micro Review Desktop

An Anki add-on (Anki 2.1.49 to current Anki 26.09 and forks built on it, such as the FSRS-7 / RWKV fork; Windows)
that does on the PC what Micro Review Focus does on the phone:

- **A few cards every so often**: 5 cards after every 20 minutes of actual use of the PC (mouse or keyboard input
  in the last 30 seconds; never while you're away), in a window that stays on top until they're answered. Pass or Fail, each counts; **Bury** puts a card away until
  tomorrow and doesn't count. **Keep going** afterwards, or **Study now** from the tray, for as long as wanted.
- **Never in the middle of a game**: not while a Steam game runs, while one of the listed processes runs
  (Minecraft by default), or while a full-screen app other than a browser is in front.
- **One emergency skip a day**, and **Snooze** (15 minutes, 1 hour, 3 hours, until tomorrow) from the window or the tray.
- **Only while the PC is online.**
- **Pauses the phone** while the PC is used: no cards and no gate on the phone until 5 minutes after the last mouse
  or keyboard input. Pair the phone with the code in Tools › Micro Review Desktop.
- **Syncs before and after the cards**, in the background (no sync window, nothing comes to the front): before, to
  get the phone's latest answers, so a card isn't reviewed twice; after, then it tells the phone which cards it reviewed. A
  full sync (after a note type change, for example) is left to Anki's Sync button, with a message in the tray.
- **Starts with Windows**, in the tray; closing Anki's window keeps it there (File › Exit or the tray's Quit Anki quit).
- The card's buttons wait a second after the window appears, and the window doesn't take the keyboard until it is
  clicked: typing goes on in the app in use.

Cards come from Anki's own scheduler and look as in Anki's reviewer (template, styling, pictures, other add-ons).

## How it works

| File | Role |
|---|---|
| `controller.py` | The 5-second tick: time of use, sitting time and stand-up breaks, snooze, online check, game check, the phone; quiet syncs (`col.sync_collection` in the background, Anki 2.1.66+; Anki's own sync before); tray, autostart, close to tray (and Anki kept running when the card window closes with Anki's window hidden). |
| `prompt.py` | The card window (`CardPrompt`). |
| `breaks.py` | The stand-up break (`BreakPrompt`): after `stand_up_minutes` of sitting (`SittingClock`: time without input counts until 3 minutes, which is a break), a small window top right that doesn't take the keyboard, with the next desk exercise (`DESK_MOVES`, the phone's Move desk breaks, in turn); Done, "5 min", or stepping away (counted once there was input after it appeared). The spell breaker (`SpellPrompt`): after `spell_minutes` with a distraction in front (`distraction()`: a word of `distractions` in the window's title or program; `DistractionClock`, short exits included), a window in the middle that takes the keyboard: "Close it" minimizes the distracting window, "5 more minutes" must be held (`HoldButton`, `spell_hold_seconds`: longer each time), "On purpose" leaves the stretch alone. During a game, either is a tray notification instead. |
| `cards.py` | Cards through `col.sched` (`getCard`, `answerCard`, `nextIvlStr`, `bury_cards`), in the chosen decks; the current deck is put back afterwards. |
| `windows.py` | Idle time (`GetLastInputInfo`), games (Steam's `RunningAppID`, windows and processes, full screen), the `Run` registry key. Answers "nothing" elsewhere. |
| `relay.py` | ntfy.sh: tells the phone how many seconds to stay paused, and that the PC synced; online check. |
| `rules.py` | The pure logic, unit tested (`desktop/tests`). |
| `store.py` | State (pairing code, skips, snooze) and the log, in `user_files` (kept by Anki across updates). |
| `settings.py` | Tools › Micro Review Desktop. |

The phone side is `PcActivity.kt` / `PcPairing.kt`: the topic is `mrf-<code>`; each message is a number of seconds
counted from the relay's time; the latest message counts ("420" while in use, refreshed every 4 minutes; the
remainder when the use stops; "0" to resume). After a sync that carries the PC's answers (its own after the cards,
or Anki's), the PC posts "synced" and the cards reviewed since its last message to `mrf-<code>-sync`
("synced 1712345678901:0,...", note ID and card ordinal, the latest 200): the phone passes over them until it syncs
AnkiDroid, which it only does as its next card screen closes (AnkiDroid can't sync unseen).

## Tests

- `python3 -m pytest desktop/tests`: the pure logic (CI runs it).
- In a real Anki 2.1.49 (`pip install anki==2.1.49 aqt==2.1.49 PyQt5==5.15.11 PyQtWebEngine==5.15.7 "protobuf<3.21"`,
  with `PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION=python`), under `xvfb-run`, with a throwaway profile and a driver
  add-on: the window, the 1-second lock, answers, bury, Keep going, skip, snooze and quit were checked this way.
  Also in Anki 26.09.4 with Qt 6 (`pip install aqt==26.9.4`; under Xvfb, Qt 6 needs `libxcb-cursor0`).
  Not checkable there: the Windows calls (idle time, games, autostart) and the tray (no tray under Xvfb).
