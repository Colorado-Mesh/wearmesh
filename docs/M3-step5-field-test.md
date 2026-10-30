# M3 step 5: field test

The exit test for M3, from the design: a field test with the Colorado mesh, with messages going both ways between the watch and a phone client on another radio. Steps 1 to 4 each passed on the watch; this run does it all once, on purpose, away from home.

## Set-up

- **Watch:** WearMesh 0.0.12 or later, connected to the Heltec T1 (`🔥W0RMT-T1n`), in Check-in mode every 15 minutes.
- **The other side:** a phone running the MeshCore app on a second radio (W0RMT-M9, say), marked a favourite on the T1, and the T1 a favourite on it.
- **Distance:** far enough that the two radios reach each other through at least one repeater, not directly. The log's "flooded" or "sent along a known path" line and the phone's path view show which.
- Charge the watch first and note its battery when you set off.

## Checks

| # | What | Mode | Pass when |
| --- | --- | --- | --- |
| 1 | Phone sends a direct message to the watch | Check-in | It notifies at the next check-in (up to 15 min) and opens to its conversation |
| 2 | Reply from that notification: a quick reply through **Quick reply**, and a spoken one through the watch's own reply icons | Check-in | The phone shows each; the watch shows Delivered |
| 3 | Watch sends a dictated direct message from the app | Check-in | It goes at once (no waiting for a check-in); the phone shows it; Delivered |
| 4 | Watch posts on #testing (or a channel you share) | Check-in | Sent to radio; the phone sees it on that channel |
| 5 | Phone replies on that channel with `@[🔥W0RMT-T1n]` | Check-in | The watch notifies (a mention notifies even on a channel that is off) |
| 6 | Switch to Live; repeat 1 and 3 | Live | The direct message notifies within seconds; the reply goes at once |
| 7 | Start a direct message from Home › New message to someone not yet messaged | Either | It starts the conversation; Delivered if they are in reach |
| 8 | Out of reach: turn the T1 off, send, turn it on within 30 min | Check-in | The message waits ("Sending…") and goes at the next check-in |

Pull the log afterwards:

```
adb shell run-as org.coloradomesh.wearmesh cat files/m0-log.txt > m3-field.txt
```

## Results (30 October 2026): M3 passes

Run from 08:38 to 13:52, in Check-in except for check 6, with W0RMT-M9 on the phone as the other side.

| # | Result | From the log |
| --- | --- | --- |
| 1 | Pass | DMs from 🔥 W0RMT 02, 🔥 W0RMT 03 and W0RMT-M9 notified at the check-in that brought them |
| 2 | Pass, after a fix | The watch's reply icons (voice, keyboard, emoji) work: `Notification: reply`, then Delivered. The quick replies did not show, on the notification or in the keyboard: the Pixel Watch ignores those given to a reply action. Fixed in the same pull request: a **Quick reply** button opens the app on the conversation's quick replies, and one tap sends. Both passed at 13:52 |
| 3 | Pass | DMs to 🔥 W0RMT 03 (11:26) and 🔥 W0RMT 02 (11:42) went at once, along a known path, acknowledged in 0.6 s |
| 4 | Pass | "Message on channel 2 sent" (#testing), 11:44 |
| 5 | Pass | "Notified: #testing, mention", 11:45 |
| 6 | Pass | Live connected in under 2 s at 13:24; a DM from W0RMT-M9 notified at once, and the reply was acknowledged in 0.9 s |
| 7 | Pass, with a miss | New message to a node not messaged before (`de8fe67a6234`): flooded three times at 11:10 and again at 11:18, Not confirmed each time, so out of reach. The conversation started as it should |
| 8 | Pass | The send at 12:18 could not connect (radio off); Check now at 12:21 sent it. The radio came back with its clock at 1970, and the watch set it on that connection first |

**Delivery times.** A message waiting for a check-in arrived 5 to 12 minutes after it was sent (medians), as a 15-minute interval gives; on Check now, in seconds. One message reached the radio half an hour after the phone sent it, in Live, so on the mesh's side. Acknowledged DMs came back in 0.6 to 0.9 s.

**Reliability.** Of about 40 connections, two failed: a Check now at 11:24 that stalled for 90 s (the next one worked) and the one with the radio switched off, on purpose.

**Battery.** 99% at 08:38 to 80% at 13:24, about 4% an hour: hands-on testing, with about 20 connections opened by hand on top of the schedule. The overnight run before it used 1.2% an hour.

**Favourites.** The radio listed the same favourites on every full read during the run, and none was dropped: marking the people you message as favourites keeps them on a full contact list (see step 2).

Still to note: where the two radios were and how far apart, and how many repeaters lay between them.
