# M4 step 3: carry

The exit test for M4, from the plan: a day's carry with the Tile and the complication in use, using no more battery than a Check-in day. The runs were on the Pixel Watch 5, 30 October to 1 November 2026, with the Tile and complication on the watch throughout: steps 1 and 2, then the changes in [M4-glance-changes.md](M4-glance-changes.md) from 31 October at 09:51.

## Results

| Run | Radio | Mode | Window | Battery | Drain |
| --- | --- | --- | --- | --- | --- |
| Night 1 | T1, zephcore 1.17.7 | Check-in, 15 min | 30 Oct 21:06 to 31 Oct 05:36 | 40% to 28% | about 1.4% an hour |
| Day | WisMesh Tag, stock MeshCore 1.18.0 | Live | 31 Oct 06:15 to 19:28 | 78% to 27% | about 2.1% an hour to 08:38, then about 4% |
| Night 2 | T1, zephcore 1.17.7 | Check-in, 15 min | 31 Oct 23:12 to 1 Nov 04:28 (6.3 h; the clocks went back an hour) | 93% to 88% | about 0.8% an hour; about 1.0% over 22:12 to 05:13 |
| M2 baseline | T1 | Check-in, 15 min | 28 to 29 Oct, night | | about 0.8% an hour |
| M2 baseline | T1 | Live | 29 Oct, day | | about 2.0% an hour |

**M4 passes the exit test.** Night 2 matches the M2 night, and the Live morning on the Tag matches the M2 Live day. The Tile and complication add nothing measurable.

## Why night 1 and the day ran high

The watch's battery statistics after the day run (`adb shell dumpsys batterystats --charged`, 59 hours on battery since the last full charge) settled it:

- WearMesh used about 120 of 685 mAh, about 17%, which averages about 0.4% of the battery an hour. Live's link cost about 0.5% an hour over the day, and most of the rest was the app on screen during testing.
- The Tile was asked for content 28 times and the complication 323 times in 2.5 days, so their updates were not the cost.
- Wi-Fi woke the watch about 67,000 times (`wlan_rx_wake`), and the Wi-Fi and Bluetooth chip about 58,000 (`bcmsdh_sdmmc`): about once every 3 seconds. System processes, not apps, did most of the work with the screen off.

Wireless debugging keeps Wi-Fi on and connected. It was turned on at about 09:50 on the day, to install, and the drain doubled from then on, whatever the message traffic. It was likely also on through night 1, after the Tile was installed. For night 2 it was off, and the drain was back to the M2 figure.

**For later battery tests:** turn wireless debugging off after installing, and back on only to pull the log.

## Check-ins and the link

- **Night 1:** 36 check-ins with the screen off, brief reads about 2.6 s, full reads 7 to 11 s. One stalled at 19:51 the evening before: the radio answered the opening greeting after 8 s, past the 6 s wait, and the check-in held the link until its 90 s limit. meshcore-kotlin now tries the greeting and the device query three times, and a check-in whose start-up fails ends at once (PR 22).
- **Day:** one Live link held for 13 hours, reconnecting only when the app was reinstalled (08:38, 09:51, 10:06). Every greeting was answered at once.
- **Night 2:** 48 check-ins, averaging 4.7 s, longest 18.4 s. At 05:02, while the watch was being picked up and Wi-Fi turned on, one check-in opened from the app failed after three connection tries (status 147 and 133, then "the radio stopped acknowledging writes"). The next, a minute later, sent the waiting channel message, and every scheduled check-in after it worked.
- **Switching back to the T1** (20:11) kept its history and synced normally. The T1's clock had been reset (it was 1,793,497,271 s behind, so at zero), and the watch set it. The 2 messages waiting on it were stored without notifying, as the backlog rule says. Its full contact list (351) had dropped four direct-message contacts, as before; its 6 favourites stayed.

## Stock MeshCore firmware

The day run doubled as the stock-firmware test before sharing the app with testers. See [stock-firmware.md](stock-firmware.md).
