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
