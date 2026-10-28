# M2 step 3: screens

Step 3 of [M2](design.md) replaces the spike screen with the app: a home list of conversations, a conversation view, radios, settings and diagnostics, all reading the store that [step 2](M2-step2-storage.md) fills. It still sends nothing; replies are M3. Per-channel notifications and the Live/Check-in mode switch with its first-run prompt are step 4.

## The screens

| Screen | Shows | Does |
| --- | --- | --- |
| Home | A status line (radio name; "Synced 4 min ago", "Syncing…", "Live: Connected", or why the last sync failed), then direct messages newest first, then every programmed channel in the radio's slot order. Each row: name, unread count, how long ago the last message came, and the message | Tap a row to open it. **Check now** at the top; **Radios** and **Settings** at the bottom |
| Conversation | Messages oldest at the top and newest at the bottom, each with its sender, text and age. Mentions show as `@name`, bold; mentions of this radio's own name in the accent colour | Opens at the newest; the crown scrolls. Opening it marks its messages read |
| Radios | Radios this watch knows, the one in use first | Tap one to use or forget it; **Add a radio** |
| Find a radio | Radios nearby, paired ones first | Tap one to add it and use it |
| Settings | Check-in on or off; the interval (15, 30 or 60 min, tap to change); **Allow alarms** when missing; Live connect or disconnect; **Read channels and contacts again** | **Diagnostics** |
| Diagnostics | The radio's firmware, packet size and battery, what is stored, and the log | **Clear log** |
| Welcome | First run only, until a radio is chosen | **Find my radio** |

Swipe right to go back from any screen.

Decided for step 3: Home lists direct messages first, then channels by slot, so each keeps its place; every programmed channel is listed, with or without messages; **Check now** is a brief read, and Settings has **Read channels and contacts again** for a full one.

## Behind the screens

- **Opening the app syncs** with a brief read, unless Live holds the radio, a sync is running, or one ended, worked or not, in the last 2 minutes: a watch comes on screen at every glance, and a radio that is not answering should not be tried at each one.
- **The scheduled full read is now hourly**, not daily: one check-in in four at the default interval reads every channel and contact.
- **Screens update as messages are stored**: Home and the conversation view watch the database, so a check-in in the background changes what is on screen.
- **Unread** means not seen in its conversation yet. The count shows on Home; opening the conversation clears it.
- **Times** are when the watch received each message: "now", "4 min", "2 h", then the weekday, then the date. A sender's own clock can be far off.
- **Radios.** The watch keeps a list of up to 8 radios and uses one. Switching to another asks first, since it deletes the stored messages (decided for M2: one history). Forgetting the radio in use deletes its messages too, and leaves the watch on Welcome; forgetting another only takes it off the list. Either way the watch's Bluetooth settings are offered for unpairing, which an app cannot do itself. If Live holds the radio when switching or forgetting, it is stopped first.
- **Read channels and contacts again** waits while a sync is under way, such as the one when the app opens.
- **Mentions** of this radio are found by its node name, saved from the radio's greeting at each sync.
- The **dictation test** is gone until M3 needs it, with its speech and network permissions.

## Testing on the watch

Install the build and open WearMesh. It should land on Home with your radio, since the radio chosen on the spike screen carries over.

1. **Home.** The status line says "Syncing…", then "Synced just now". Direct messages, if any, come first, then all your channels in slot order, with unread counts.
2. **A conversation.** Open Public: it should start at the newest message, the crown should scroll back, and senders should be split from the text. Go back: Public's unread count is gone.
3. **Live updates.** Leave a busy channel open across a scheduled check-in; new messages should appear without reopening it.
4. **Mentions.** A message mentioning your radio's name, such as `@[🔥 W0RMT 02]`, shows in the accent colour.
5. **Settings.** Change the interval to 30 and back to 15; the log in Diagnostics shows `Check-in: every 30 min` and the next alarm. **Read channels and contacts again** should log a `full read`.
6. **Radios.** Add the test radio through **Add a radio**: it should ask before switching and, once switched, Home should start empty and fill after the sync. Switch back the same way. Then forget the test radio from the list (not in use): it should only leave the list.
7. **Check-in overnight** as before, to see the hourly full reads: about one `full read` line an hour in the log.

Note anything that is hard to read or reach on the round screen; layout is the part most likely to need a second pass.
