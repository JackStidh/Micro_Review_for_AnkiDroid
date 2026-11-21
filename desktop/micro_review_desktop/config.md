Micro Review Desktop: a few cards every so often while you use your PC. Easier to change from **Tools › Micro Review Desktop** than here.

- `interval_minutes`: cards every this many minutes of actual PC use (mouse or keyboard).
- `cards_per_prompt`: how many cards each time. Pass or Fail, each counts; Bury doesn't.
- `decks`: deck names, in priority order; empty for Anki's current deck.
- `pass_fail_only`: only Fail (Again) and Pass (Good).
- `show_intervals`: show when the card comes back on its buttons ("Pass · 4d").
- `play_audio`: play the card's audio.
- `skips_per_day`: emergency skips per day.
- `require_internet`: no cards and no phone pause while the PC is offline.
- `input_within_seconds`: no mouse or keyboard input for this long: you're away; the time doesn't count and no cards come.
- `pause_phone_while_in_use`, `phone_pause_minutes`: tell Micro Review Focus on the phone to pause until this long after your last input (through ntfy.sh).
- `gaming_shutoff`: no cards while a game runs: a Steam game, a process of `game_processes` (`"name.exe"`, or `"name.exe:title"` for a window title containing title), or, with `fullscreen_counts_as_game`, a full-screen app other than the `browsers`.
- `start_with_windows`, `start_hidden`: start Anki at sign-in, in the tray.
- `keep_running_when_closed`: the window's close button keeps Anki in the tray (File › Exit still quits).
- `sync_after_cards`: sync with AnkiWeb before the cards (the phone's latest answers) and after them, if you're logged in, in the background, then tell the phone to sync too.
- `morning_hour`: when "Snooze › Until tomorrow" ends.
- `stand_up_minutes`: after this many minutes of sitting at the PC, a stand-up break with a desk exercise (0: off). A few minutes without input counts as a break; so does stepping away while it shows.
- `spell_minutes`: after this many minutes with a distraction in front (short exits included), a prompt in the middle of the screen: "Close it" sends the window to the taskbar, "5 more minutes" must be held (longer each time), "On purpose" leaves that stretch alone (0: off). During a game, a tray notification instead.
- `distractions`: words that make a window a distraction when its title (a browser tab's) or its program's name contains them, case ignored.

