# M5 step 2: ready for strangers

Step 2 of the sharing and release plan ([design.md](design.md)): what someone who is not the
developer needs before a first release. Decided 2 November 2026: message text is hidden in the
log by default, and testers get the log off the watch with adb for now; a way without adb is
for later, before Play testers, who will not have it.

## What changed

| Part | What it does |
| --- | --- |
| Settings › About WearMesh | The version ("0.1.0-dev (25)"), the radio in use with its model and firmware, Colorado Mesh, not affiliated with MeshCore, GPL-3.0 and where the source is, and how to report a problem |
| Message text in the log | Off by default, also in releases: a received message is logged as "(text hidden, 42 characters)", with who sent it and when. Diagnostics › Message text in log turns it on, from that moment; lines already written stay as they are. Names of channels and contacts are still logged. Lines written before this version may hold message text: Clear log removes them |
| Save log | Diagnostics › Save log copies the log to the watch's Download/WearMesh folder, as `wearmesh-log-2026-11-02-183005.txt`, where adb can fetch it: a release build's own files cannot be read with `adb shell run-as`, which works only for debug builds |
| The log file | Now `wearmesh-log.txt` (the old `m0-log.txt` is moved on first start). Past 2 MB it becomes `wearmesh-log.old.txt` and a new one starts, so the newest 2 to 4 MB are kept, not everything for ever. Clear log empties both |
| Crashes | An uncaught crash is written to the log, with its stack (15 frames for the error and each cause), before Android closes the app |
| Log wording | Live's lines say "Live:", not "M0:" |
| Alarms & reminders | In Check-in without it, Home says Check-in needs it to sync on time while the watch sleeps, with Allow alarms, instead of only Settings saying so |
| Radio facts | The radio's model and firmware are kept from Check-in too, not only Live, for About; cleared when another radio is chosen |

## Getting the log off a watch

On the watch: Settings › Diagnostics › **Save log**. Then, with adb connected over wireless
debugging:

```bash
adb pull /sdcard/Download/WearMesh/
```

That fetches every saved log into a `WearMesh` folder. A debug build's log can still be read
directly, as before, now under its new name:

```bash
adb shell run-as org.coloradomesh.wearmesh cat files/wearmesh-log.txt > wearmesh-log.txt
```

## Testing on the watch

1. **About.** Settings › About WearMesh shows 0.1.0-dev (25), the radio's name, model and
   firmware (after a sync), and the Colorado Mesh, licence and source lines.
2. **Text hidden.** Diagnostics › Message text in log says Hidden. Receive a message in Live: its
   log line shows "(text hidden, N characters)". Turn it on, receive another: its text shows. Turn
   it off again.
3. **Save log.** Tap Save log: a note gives the file's name. `adb pull /sdcard/Download/WearMesh/`
   fetches it, and it holds the log.
4. **The old log moved.** The saved log starts with lines from before this version (the old file
   was moved, not lost).
5. **Alarms.** In Check-in, turn off Alarms & reminders for WearMesh in the watch's settings:
   Home shows the note and Allow alarms, which opens the setting. Allow it again: the note goes.
6. **A crash** cannot be caused on purpose from the app. If one happens in testing, the saved log
   shows "Crash on thread …" with the stack.
