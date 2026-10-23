# M0: Bluetooth spike

M0 answers the Bluetooth questions on a real watch before any messaging UI is built. Its exit criteria, from [design.md](design.md):

- The link to the companion radio holds on the Pixel Watch 5 with the screen off for 30 minutes.
- The packet size (MTU) the watch negotiates is recorded.
- On-device dictation quality is checked.

The spike reads from the radio only. It never sends on the mesh and never changes the radio's settings, channels, contacts or clock.

## Before you start

- A companion radio on BLE companion firmware, charged, with its PIN to hand.
- **Disconnect the MeshCore app on your phone from that radio**, or turn the phone's Bluetooth off. The radio serves one client at a time, and the watch will not find a radio that is connected to something else.
- The watch on a charger until the test starts, then off it, so the battery numbers mean something.

## Running it

1. Install the debug build (see the README) and open **WearMesh**.
2. Tap **Scan for radios**, allow Bluetooth and notifications, and tap your radio. Enter its PIN when the watch asks to pair.
3. Wait for the status to read **Connected**. The log shows the radio's name and firmware, the packet size, the contact count, each channel, and any queued messages.
4. **Screen-off test.** Let the screen go off, put the watch on, and leave it for at least 30 minutes. If you can, have someone post on one of the radio's channels during that time.
5. Wake the watch and read the log: every drop is logged as `link down after N min` with a reason, and every reconnect try is logged.
6. **Dictation test.** Turn on airplane mode, and if the watch is paired to a phone turn the phone's Bluetooth off too. Tap **Test dictation** and say a short field message, for example "At the trailhead, heading up in ten". Repeat with Wi-Fi on for comparison. Each result is logged with its time and what network the watch had.
7. Tap **Disconnect** when done.

## Getting the log off the watch

The screen shows the newest 80 lines. The whole run is in a file on the watch:

```bash
adb shell run-as org.coloradomesh.wearmesh cat files/m0-log.txt > m0-log.txt
```

Or watch it live: `adb logcat -s WearMesh`. **Clear log** at the bottom of the screen starts a fresh file.

## What to record

Copy these into an issue or the design doc's open questions.

| Question | Where to find it |
| --- | --- |
| Did pairing and the first connection work? How many tries? | `Link: Connecting…`, `Connection try N failed` lines |
| Packet size | `M0: link up, packet size N bytes` (the protocol wants at least 172) |
| Did the link hold for 30 min with the screen off? | Any `M0: link down after` lines, and their reasons |
| Were messages received with the screen off? | Channel and DM lines timed during the test |
| Watch battery used | `M0: watch battery N%` every 5 minutes |
| Dictation offline: did it work, how long, how accurate? | `M0 dictation` lines with `network: none` |
| Dictation online, for comparison | `M0 dictation` lines with `network: Wi-Fi` |
| Radio firmware and model | `Radio:` lines at connect |

## Known limits of the spike

- One radio, remembered on the watch; scanning again replaces it.
- After a drop it reconnects after 30 s, then 60, 120, 240 and every 5 minutes after that. It does not give up while the service runs.
- No messages are kept beyond the log, and none are sent.
