# M2 step 1: background Check-in

Step 1 of [M2](design.md) answers one question before any screens are built: can a scheduled job reach the radio with the screen off and the app closed, without a foreground service? If it can, Check-in mode is a WorkManager job. If Wear OS refuses, the fallback is a short foreground service started by the job, which has its own limits on starting from the background.

The spike app now has two more controls:

- **Check-in: on / off** schedules a job every 15 minutes (the system's minimum) that connects, collects what the radio has queued, logs it and disconnects. It survives the app closing and the watch restarting.
- **Check now** runs one check-in straight away, for a quick try.

A check-in is skipped while the Live link is up, since the radio serves one client at a time. Like everything in the spike, it only reads from the radio.

## Running the test

1. Install this build. Disconnect the phone's MeshCore app from the radio, and tap **Disconnect** if Live is running.
2. Tap **Check now** and watch the log: a `Check-in (asked for): starting` line, the link steps, any queued messages, then `Check-in: done in N s`. That shows the job can connect while the app is open.
3. Tap **Clear log**, then turn **Check-in: on**. The first scheduled run usually comes within a minute.
4. Swipe the app away, let the screen go off, and leave the watch overnight, off the charger if you can, so Doze behaves as it would on the wrist. Put the radio within range and leave it on.
5. In the morning, pull the log:

   ```bash
   adb shell run-as org.coloradomesh.wearmesh cat files/m0-log.txt > checkin-log.txt
   ```

6. Turn **Check-in: off** afterwards, or leave it on for the full-day carry later in M2.

## What the log shows

Each run writes a `starting` line with its conditions, and a `done` or `failed` line:

```
Check-in (scheduled): starting; screen off, Doze, standby bucket working set, battery-optimised, watch battery 81%
Check-in: done in 9.8 s (link up after 1.4 s), 3 new messages; watch battery 81%
```

| Question | Where to look |
| --- | --- |
| Does a background job connect with the screen off? | `done` lines whose `starting` line says `screen off` |
| How often does it really run overnight? | Gaps between `starting` lines; 15 minutes at best, longer when Doze holds it back |
| Does Doze stop it altogether? | Long gaps after `Doze` appears, and how long they get |
| How long does each run hold the radio? | `done in N s`; most of it is reading the radio's contact list |
| Battery cost | `watch battery` at the start and end of the night |
| Failures | `failed after` lines and the link lines just before them |

If no run happens at all with the screen off, or every one fails, the fallback (a short foreground service) is the next thing to try.
