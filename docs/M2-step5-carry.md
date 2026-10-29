# M2 step 5: full-day carry

The exit test for M2, from the design: one full day of carry in Check-in mode, with battery drain measured. A day in Live followed, for comparison. Both runs used the Pixel Watch 5 with the Heltec Mesh Node T1 (firmware 1.17.7-zephcore, 15 channels, about 350 contacts) and app version 0.0.6 (step 4).

## Results

| | Check-in, every 15 min | Live |
| --- | --- | --- |
| Window | 28 Oct 13:28 to 29 Oct 07:05: 17.6 h, day and night | 29 Oct 07:36 to 16:26: 8.8 h, day |
| Watch battery | 100% to 77% | 98% to 80% |
| Drain, worn in the day | about 1.5% an hour | about 2.0% an hour |
| Drain, overnight | about 0.8% an hour | not run |
| Syncs | 81 (72 scheduled, 9 on opening the app); 77 finished | one link, held all day |
| Failures | 4, all on the radio link (the radio stopped acknowledging twice, notifications not turned on once, a full read hung for 90 s once); the next check-in worked each time | 2 drops (status 147) |
| Time per sync | brief read median 3.3 s (longest 14.9 s); hourly full read 10 to 24 s, once 69 s | |
| Messages | 344 stored, none twice; each waits up to 15 minutes | 134; a median 7 s after sending |

**Check-in passes the exit test.** At this rate a charged watch lasts more than two days. Ten check-ins needed their second connection try (status 133 or 147), and every one got through. One alarm came about 6 minutes late; the watch restarted once and Check-in set its alarm again. Live costs about a third more battery in the day, less than the 2.3% an hour M0 measured. The two runs covered different hours and use, so the comparison is fair rather than exact.

**A Live bug.** Live's reconnect pause was a Handler delay, which stops while the watch sleeps, the lesson from M0 that the design had already noted for reconnects. Both pauses meant to last 30 s ran long: 1.7 minutes after the 07:43 drop, and 17.7 minutes after the one at 16:08, which ended only when the watch woke. Live received nothing in between.

**Not measured.** The log had no line for notifications shown, and Check-in logged no message delays, so neither could be counted.

## Changes in this step

- **Live reconnects on time.** The pause is timed by an exact allow-while-idle alarm as well as the Handler, whichever comes first. The alarm goes to a small receiver, `LiveReconnectReceiver`, since the system keeps the watch awake while a receiver runs; it starts the reconnect, and Live holds a partial wake lock until the link is up or the attempt has failed (90 s at most). In Doze the system lets such alarms fire about every 9 minutes at most, so a pause can still stretch to that, never to the next time the screen comes on. Without "Alarms & reminders" allowed, an inexact alarm that still fires in Doze is used.
- **Notifications are logged.** Each time notifications are posted, one line names the conversations, marks those notified only for a mention of the radio, and says whether it buzzed:
  `Notified: Ann; #den, mention; buzzed`. The backlog after choosing a radio and notifications turned off for WearMesh each log a line too.
- **Check-in logs message delays.** A check-in's result line gives the median and longest time from sending to storing for its new messages: `13 new messages (delay median 7 min, longest 14 min), 0 already stored`. The sender's clock may be off, so a message from "the future" is counted apart. Live already logged each message's age.
- **Quit.** Settings › **Quit WearMesh** stops Live and cancels the Check-in alarm, then closes the app, so nothing runs in the background (decided 28 October). A check-in already running finishes and sets no next alarm. Opening the app again carries on in the mode in use before: Live connects, or Check-in syncs and sets its next alarm. Restarting the watch does not undo Quit.

## Testing on the watch

1. **Quit in Check-in.** Settings › Quit WearMesh. The app closes. Wait past the next check-in time: the log (pulled with adb) should show `Quit: …` and no check-in after it. Open the app: `Opened after Quit: back to Check-in`, a sync, then `Check-in: next at …`.
2. **Quit in Live.** Switch to Live, then Quit. The Live notification goes away and the log shows `M0: stopped`. Open the app: Live connects again.
3. **Live reconnect.** In Live, with the screen off, switch the radio off for about a minute, then on. The log should show `M0: reconnecting in 30 s`, then within a minute or so (up to about 9 in Doze) `M0: reconnecting now (woken by the alarm)` or a reconnect by the timer, and `M0: link up`.
4. **Notification lines.** A DM, or a mention on Public, should log `Notified: …`.
5. **Delays.** Each check-in with new messages shows the delay in its result line.
