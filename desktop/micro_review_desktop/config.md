Micro Review Desktop: a few cards every so often while you use your PC. Easier to change from **Tools › Micro Review Desktop** than here.

- `interval_minutes`: cards every this many minutes of actual PC use (mouse or keyboard).
- `cards_per_prompt`: how many cards each time. Pass or Fail, each counts; Bury doesn't.
- `decks`: deck names, in priority order; empty for Anki's current deck.
- `pass_fail_only`: only Fail (Again) and Pass (Good).
- `show_intervals`: show when the card comes back on its buttons ("Pass · 4d").
- `play_audio`: play the card's audio.
- `skips_per_day`: emergency skips per day.
- `require_internet`: no cards and no phone pause while the PC is offline.
- `idle_after_seconds`: no input for this long: the PC isn't in use.
- `pause_phone_while_in_use`, `phone_pause_minutes`: tell Micro Review Focus on the phone to pause until this long after your last input (through ntfy.sh).
- `gaming_shutoff`: no cards while a game runs: a Steam game, a process of `game_processes` (`"name.exe"`, or `"name.exe:title"` for a window title containing title), or, with `fullscreen_counts_as_game`, a full-screen app other than the `browsers`.
- `start_with_windows`, `start_hidden`: start Anki at sign-in, in the tray.
- `keep_running_when_closed`: the window's close button keeps Anki in the tray (File › Exit still quits).
- `sync_after_cards`: sync with AnkiWeb after the cards, if you're logged in.
- `morning_hour`: when "Snooze › Until tomorrow" ends.
