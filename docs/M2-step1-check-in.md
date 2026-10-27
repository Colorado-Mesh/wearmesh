# M2 step 1: background Check-in

Step 1 of [M2](design.md) answers one question before any screens are built: can Check-in reach the radio every 15 minutes while the watch sleeps, screen off and app closed?

## Night 1: a WorkManager job (25 October 2026)

No. A WorkManager periodic job connected fine with the screen off, but Doze held it back:

| Time | Screen | Result |
| --- | --- | --- |
| 20:07, 20:27, 20:45 | On | Connected; 7 to 21 s each; gaps of 15 to 20 minutes |
| 21:00 | Off, Doze | Connected in the background; done in 17 s |
| 21:00 to 03:00 | Off, Doze | No run for 6 hours (about 24 missed) |
| 03:00 | Off, Doze | Stopped by the system and rerun; the CPU then slept mid-run, so a 12 s connection limit took 30 s, a 1.5 s pause took 6 minutes, and the run took 6.7 minutes |
| 05:00 | On | Failed: the radio did not answer 3 tries |

Watch battery fell from 88% to 80% over about 9 hours, low mainly because the job seldom ran. Two lessons: Doze defers jobs for hours, and a running job does not keep the CPU awake, so a check-in needs its own wake lock.

## Night 2: exact alarms

Check-in now runs on an exact allow-while-idle alarm, which fires in Doze too (Android allows one at most about every 9 minutes). Each alarm sets the next, then starts a short foreground service that does the check-in, holding a wake lock for at most 2 minutes. If the system refuses to start the service, the check-in runs inside the alarm instead, with a 40 s limit, and the log says so. The alarm is set again after a restart, an app update, or when the permission is granted.

### Result (26 to 27 October 2026)

Yes. Over 9 h 16 min, from 19:48 to 05:04, every check-in ran:

| Question | Night 2 |
| --- | --- |
| Check-ins | 38 of 38; 35 of them in Doze |
| Gaps between them | 15.0 to 15.1 minutes, none missed |
| Time each took | 7.1 to 16.9 s, median 9.0 s; link up after 1.5 to 5.8 s |
| Foreground service refused | Never; no check-in ran inside the alarm |
| Stopped part-way | Never |
| Failures | None. 3 check-ins hit a Bluetooth status 133 (4 failed tries in all) and recovered on a retry; one needed all 3 tries |
| Messages collected | 72, on Public, #testing, #bot, #weather and #cm-reach |
| Watch battery | 100% to 92%: about 0.9% an hour, against about 2.3% an hour for Live in M0 |

So Check-in is the background mode for M2: at this rate a 16-hour day of check-ins uses about 14% of the watch's battery.

Things for step 2 to handle:

- **Duplicates.** The same message can come twice: "🌀flux: Hey!" came twice in one check-in at 20:33, and a #bot reply first collected at 21:03 came again at 00:33. Storage should drop a message whose sender, channel, sent time and text match one it already has.
- **Start-up on every check-in.** A check-in already closes as soon as the radio's queue is empty, yet with no new messages it still takes about 7 s, most of it after the link is up: each one reads the whole contact list (about 350) and all 40 channel slots before collecting. At 23:03 the radio did not answer the contact list request at all, which added 8 s. With contacts and channels stored, a check-in can ask only for contacts changed since the last one (the shared client already sends a "since" time, but a new client starts from zero) and read the channel slots once a day or when asked; that should shorten each one and save some battery.
- **Little room on connection tries.** One check-in used all 3 tries. Step 2 can allow a fourth, a few seconds apart, since a check-in that fails waits 15 minutes for the next.

Exact alarms need **Alarms & reminders**, which is off for new installs on Android 14. When Check-in is on and this is not allowed, the spike screen shows an **Allow alarms** button that opens the system page. If the watch has no such page, allow it from your computer:

```bash
adb shell appops set org.coloradomesh.wearmesh SCHEDULE_EXACT_ALARM allow
```

Then turn Check-in off and on again, or wait for the app to notice the change and reschedule.

## Running night 2

1. Install this build. Disconnect the phone's MeshCore app from the radio, and tap **Disconnect** if Live is running.
2. Turn **Check-in: on**, and allow alarms if asked. The log shows `Check-in: next at HH:MM:SS`, about a minute away.
3. Tap **Check now** once and wait for `Check-in: done in N s`.
4. Tap **Clear log**. If the log shows no `next at` line after that, turn Check-in off and on again.
5. Swipe the app away, let the screen go off, and leave the watch overnight, off the charger if you can, with the radio nearby and on.
6. In the morning, pull the log:

   ```bash
   adb shell run-as org.coloradomesh.wearmesh cat files/m0-log.txt > checkin-log-2.txt
   ```

## What the log shows

```
Check-in: next at 01:15:22
Check-in (scheduled): starting; screen off, Doze, standby bucket active, battery-optimised, watch battery 81%
Check-in: done in 9.8 s (link up after 1.4 s), 3 new messages; watch battery 81%
```

| Question | Where to look |
| --- | --- |
| Do check-ins keep coming every 15 minutes in Doze? | Gaps between `starting` lines with `screen off, Doze` |
| Does each one finish in seconds, not minutes? | `done in N s`; the wake lock should keep it near night 1's 7 to 21 s |
| Did the system refuse the foreground service? | `would not start the foreground service` and `inside the alarm` lines |
| Was any run stopped part-way? | `stopped before it finished` lines |
| Battery cost | `watch battery` at the start and end of the night; more than night 1, since every check-in now runs |
| Failures | `failed after` lines and the link lines just before them |
