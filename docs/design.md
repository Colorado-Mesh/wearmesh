# WearMesh — Design Proposal

October 22, 2026 · Bud Talbot

## Summary

Build WearMesh, a standalone Wear OS app that connects directly to a MeshCore BLE companion radio and handles channel and direct messaging, with no phone in the loop. The watch replaces the phone as the companion's client: it reads channels and contacts from the radio, syncs queued messages, shows them, and sends short replies.

The official MeshCore app is closed-source, so this is a new client. It is written in native Kotlin with Compose for Wear OS, and its companion-protocol layer and Bluetooth link are shared with MeshCore TAK Bridge through the meshcore-kotlin repository, a git submodule in both apps. It lives in the wearmesh repository within Colorado Mesh, under the same licensing as the bridge app.

## Goals and non-goals

Version 1 does channel and direct messaging on the wrist and nothing else; configuration stays in the phone or web app.

**Goals**

- Scan for, pair with, and reconnect to one MeshCore BLE companion radio
- Read the channel list (names and indexes) and known contacts from the radio
- Sync queued messages and show them per channel or contact conversation, newest at the bottom
- Send channel messages and direct messages to known contacts by quick reply, dictation, or keyboard
- Notify on new channel messages, with an unread count on a Tile or complication
- Run with no phone present (standalone Wear OS app)

**Non-goals for v1**

- Adding, editing, or discovering contacts on the watch (contacts come from the radio)
- Radio settings, channel creation or key entry, repeater and room-server admin
- Maps, location sharing, telemetry, TAK integration
- Coexisting with the phone app on the same radio at the same time
- Wear OS on iPhone-paired watches is not a target; nothing should prevent it

## Background and constraints

The companion protocol is small and documented, and its offline message queue is what makes a battery-friendly watch client possible.

**Companion protocol**

- The radio is the server and the app is the client. Over BLE, each characteristic write or notification is one complete frame: the app writes to an RX characteristic and subscribes to notifications on TX ([eastmesh wiki](https://wiki.eastmesh.au/meshcore/companion-protocol/)).
- Devices advertise a MeshCore service UUID and usually a name starting with "MeshCore-" ([docs.meshcore.io](https://docs.meshcore.io/companion_protocol/)).
- The documented startup flow fetches contacts, then each channel slot with CMD_GET_CHANNEL, then queued messages with CMD_SYNC_NEXT_MESSAGE ([docs.meshcore.io](https://docs.meshcore.io/companion_protocol/)).
- Messages queue on the radio while no app is connected. The app pulls them one at a time until RESP_CODE_NO_MORE_MESSAGES. The radio pushes PUSH_CODE_MSG_WAITING (0x83) when a new one arrives ([MeshCore wiki](https://github.com/meshcore-dev/MeshCore/wiki/Companion-Radio-Protocol/25369eb5d8b48f37e30681248ac100e2d799c9e6)).
- Sending to a channel is CMD_SEND_CHANNEL_TXT_MSG (3) with a channel index; the current doc lists indexes 0–7. Direct messages use CMD_SEND_TXT_MSG to a known contact, and the radio replies with an expected ACK code; the contact list comes from CMD_GET_CONTACTS.
- Firmware v1.15 added channel-data frames and may expose a Nordic DFU service beside UART, so a client should not assume UART is the only service ([pyMC_core notes](https://github.com/pyMC-dev/pyMC_core/pull/72/files)).

**One client at a time**

The watch is the radio's only client: while it is connected, the phone app is not. Supporting several clients on one radio is out of scope.

**Wear OS**

- Wear OS uses the standard Android BLE stack; the watch acts as BLE central.
- Keeping a link open in the background requires a foreground service of type connectedDevice, or the companion device presence API ([Android BLE background guide](https://developer.android.com/develop/connectivity/bluetooth/ble/background)). Wear OS 5 and 6 freeze background processes aggressively otherwise.
- Input is the hard part: small keyboard, dictation, and quick replies. The first target is the Pixel Watch 5, where on-device dictation may be good enough to be a primary input.
- Battery is roughly an order of magnitude smaller than a phone's, so radio-on time is the main cost to manage.

## Architecture

The app is a standard layered Wear OS app whose bottom layer is a pure-Kotlin protocol module shared with MeshCore TAK Bridge.

```
 Wear OS app on the watch                          
┌───────────────────────────────────────┐
│ Compose for Wear OS screens           │
│ Tile, complication, notifications     │          ┌─────────────┐
│ Repository and storage                │          │  LoRa mesh  │
│ Connection service                    │          └──────▲──────┘
│ Shared protocol module (pure Kotlin)  │◄── BLE ──►  Companion radio
└───────────────────────────────────────┘          (one client, holds
                    ▲                               channels and queue)
                    └── shared ── MeshCore TAK Bridge (phone)
```

Only the connection service touches BLE; everything above it works on parsed messages and channels. The protocol module has no Android dependencies, so it is unit-tested on the JVM and reused as is in the bridge app.

- **Language and UI:** Kotlin, Compose for Wear OS (Horologist where useful), minimum Wear OS 4 (API 33) to get the modern Bluetooth permissions.
- **Manifest:** standalone flag set; BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_CONNECT, FOREGROUND_SERVICE_CONNECTED_DEVICE, POST_NOTIFICATIONS.
- **Concurrency:** coroutines and Flows; the connection service exposes link state and an incoming-message Flow to the repository.

## Connection strategy and power

The app has two connection modes, one setting in Settings › Mode that stays until changed: Live for instant alerts, Check-in for battery life. A new install starts in Live and is offered Check-in once (M2 step 4, [M2-step4-notifications.md](M2-step4-notifications.md)). A third, Manual mode (connect only with the app on screen) was in the first plan and was dropped: opening the app syncs in either mode. Settings › Quit WearMesh stops both until the app is next opened, which carries on in the same mode (M2 step 5).

| Mode | How it works | Alerts | Battery cost | Use when |
| --- | --- | --- | --- | --- |
| Live | Foreground service (connectedDevice) holds the BLE link; PUSH_CODE_MSG_WAITING triggers sync | Seconds | Highest; link always up (about 2.3% an hour) | Active ops, events, nets |
| Check-in | Connect on a timer or when the app opens, drain the queue, disconnect | Up to one interval late | Low; radio mostly idle (about 1% an hour overnight) | All-day carry |

The Check-in interval is user-set: 15 minutes by default, 30 or 60 as options. Check-in runs on exact allow-while-idle alarms, each starting a short-lived foreground service with a wake lock for one sync. A WorkManager job was tried first and Doze held it back for 6 hours overnight ([M2-step1-check-in.md](M2-step1-check-in.md)). Exact alarms need the user to allow "Alarms & reminders" once.

Connection lifecycle:

1. First run: scan filtered by the MeshCore service UUID, user picks the radio, system pairing dialog takes the PIN, app stores the address.
2. Connect: GATT connect, request a larger MTU, subscribe to TX, send CMD_DEVICE_QUERY and APP_START, set device time from the watch clock.
3. Refresh channels: CMD_GET_CHANNEL for each slot; cache names locally.
4. Drain: CMD_SYNC_NEXT_MESSAGE until the queue is empty.
5. Idle (Live) or disconnect (Check-in).
6. On link loss: exponential backoff reconnect in Live, timed by an alarm that also fires in Doze (M2 step 5); give up until next interval otherwise.

One request is in flight at a time with a timeout, as the protocol doc recommends; responses are matched to the request queue in order.

Battery targets are set after the M0 spike measures real drain per mode.

## UI and input design

The app has three screens and two glanceable surfaces, built for one-handed use in the field.

| Surface | Shows | Actions |
| --- | --- | --- |
| Channel list | Channel names, unread badge, last message preview, link status chip | Tap to open; long-press to mute |
| Channel view | Messages as a scrolling list: sender, text, relative time, hop count or SNR when present | Rotary scroll; Reply button at the bottom |
| Reply | Quick replies first, then Dictate and Keyboard | One tap sends; confirmation shows Sent or Failed |
| Tile | Link status, unread total, last sender per channel | Tap opens the app; button for Check-in now |
| Complication | Unread count | Tap opens the channel list |
| Notification | Channel, sender, text | Inline reply with quick replies and dictation |

**Input**

- Quick replies and dictation are the two main paths. Quick replies are an editable list such as "Copy", "On my way", "At rally point", "Stand by", "Need help". They are stored on the watch and edited there or set once at first run.
- Dictation uses the system RemoteInput voice option. Where it needs the phone or network, it fails cleanly and the app falls back to quick replies.
- The keyboard is the last resort.
- Outgoing text is trimmed to the channel message limit, with a live character count when typing.

**Feedback**

- A channel send returns OK or ERR from the radio, not a delivery receipt, so the UI says Sent to radio, not Delivered. Direct messages carry an ACK, so they show Delivered once it arrives.
- Haptic tick on send; distinct vibration for incoming messages, adjustable per channel.

## Data and storage

The radio is the source of truth for channels; the watch keeps only a small message history it has already pulled.

- **Room database** with four tables: radio (address, name, self info, last sync), channel (index, name, mute, unread count), contact (key prefix, name, unread count), message (channel index or contact, sender, text, timestamp, SNR, direction, send status).
- **Retention:** keep the last N messages per channel (for example 200) and prune older ones; history once drained from the radio exists only on the watch.
- **Channel secrets** are not needed on the watch to send or receive: the radio does the crypto. The app stores names and indexes only, never keys.
- **Settings** (connection mode, interval, quick replies, vibration) in DataStore.
- **Time:** the watch sets the radio clock on each connect, so timestamps on sent messages are right even if the radio has no GPS.
- No cloud sync and no phone data layer; backup is off for the message table.

## Risks and open questions

The biggest unknown is BLE behavior on the Pixel Watch 5, tested in the first milestone.

| Risk | Impact | Mitigation |
| --- | --- | --- |
| BLE quirks vary by watch model (TAK-MESHCORE already fails on the Tab Active 3 but works on the Pixel 9 Pro) | App fails to pair or drops on some watches | Spike on the Pixel Watch 5 first; log GATT status codes; keep a test matrix |
| Wear OS kills the service or delays Check-in jobs | Missed or late messages | connectedDevice foreground service; test with screen off for hours; document vendor battery settings |
| Battery drain in Live mode | Watch does not last a day | Default to Check-in; measure per-mode drain early |
| Dictation needs phone or network on some watches | Voice reply unavailable off-grid | Check on-device dictation on Pixel Watch 5 in M0; quick replies as fallback |
| Protocol changes between firmware versions (V3 message frames, v1.15 additions) | Parse errors after radio updates | Handle legacy and V3 frames; check firmware version at connect; shared parser tests |
| Phone and watch both try to connect | Confusing failures | Clear "radio busy" message; optional dedicated watch radio |

**Decisions and open questions**

- [x] One client: the watch is the radio's only client while connected; no multi-client support.
- [x] Target watch: Pixel Watch 5 first.
- [x] Dictation: works offline on the Pixel Watch 5 (M0); it needs a confirmation tap before the text is returned.
- [x] Direct messages to known contacts are in v1.
- [x] Repository: wearmesh, within Colorado Mesh, same licensing as MeshCore TAK Bridge.
- [x] Packet size: the Pixel Watch 5 gets 244 bytes (MTU 247), above the 172 the protocol needs, so every frame fits (M0).
- [x] Screen off: the link held for 31 and 95 minutes and messages arrived live (M0).
- [x] Watch battery in Live mode: about 2.3% per hour, roughly 40 hours from full (M0).

## Milestones and testing plan

Four milestones, each ending in a go/no-go check; the first is a throwaway spike that answers the BLE questions before any UI work.

1. **M0 — BLE spike.** Bare Wear OS app that pairs, sends CMD_DEVICE_QUERY, lists channels, and drains the queue to a log.
   - Exit: works on the Pixel Watch 5 with the screen off for 30 minutes; MTU recorded; on-device dictation quality checked.
2. **M1 — Shared protocol module.** Extract frame encode/decode, command queue, and response parsing from MeshCore TAK Bridge into a pure-Kotlin module with unit tests from captured frames.
   - Exit: both apps build against it; tests cover legacy and V3 message frames.
   - Done 23 October 2026: [meshcore-kotlin](https://github.com/Colorado-Mesh/meshcore-kotlin) holds `protocol` (pure Kotlin, with the bridge's tests plus new ones, covering legacy and V3 channel and direct messages) and `ble` (the Bluetooth link, scanner and session), in packages `org.coloradomesh.meshcore.*`. Both apps include it as a git submodule and build it from source with `includeBuild`.
3. **M2 — Read-only client.** Channel list, channel view, notifications, Check-in mode.
   - Exit: one full day of carry with Check-in, battery drain measured.
   - Plan (25 October 2026): channel list; channel and direct-message views (read-only); grouped notifications; Live and Check-in modes; on-watch storage. Steps: (1) prove a background Check-in job can reach the radio with the screen off, [M2-step1-check-in.md](M2-step1-check-in.md); (2) storage and sync; (3) screens; (4) notifications and modes; (5) full-day carry.
   - Decided: Check-in every 15 minutes by default, 30 or 60 as options; notifications on by default for direct messages and private channels only, others turned on per channel; first run starts in Live with a prompt to switch to Check-in; opening the app always syncs; 100 messages kept per conversation.
4. **M3 — Messaging.** Reply screen with quick replies, dictation, keyboard; direct messages to known contacts; Live mode. The Tile and complication move to M4 (decided 29 October 2026).
   - Exit: field test with the Colorado mesh, messages round-trip with a phone client on another radio.
   - Decided (29 October 2026): quick replies OK, Yes, No, On my way, Copy, Call me, 73, editable on the watch; replies open in dictation with the keyboard one tap away; quick replies send at once, composed text is previewed first; in Check-in a message is sent at once, and a direct message's connection is held for all 3 tries; Reply on every message notification; channel replies start with `@[name]`; a new message picks from favourites, then recent contacts, then channels; a failed message stays with Retry and notifies; the watch sets the radio's clock when it is more than a minute off.
   - Steps: (1) outbox and sending, [M3-step1-sending.md](M3-step1-sending.md); (2) compose; (3) notification reply; (4) new message; (5) field test.
5. **M4 — Tile and complication.** Unread count, Check now and the newest message on a Tile; an unread-count complication.

**M0 results (23 October 2026): complete**

| Check | Result |
| --- | --- |
| Pair and connect | Worked on the Pixel Watch 5 |
| Packet size | 244 bytes (MTU 247); the protocol needs 172 |
| Radio | Heltec Mesh Node T1, firmware 1.17.7-zephcore (10 Oct 2026), 40 channel slots, 15 programmed (slot 7 empty), 351 contacts |
| Screen off | Passed: link held 31 minutes and then 95 minutes with no drops; messages arrived live, 3 to 30 s after sending |
| Dictation, Wi-Fi | Result in 7.6 s |
| Dictation, no network | Transcribed on the watch; needs a confirmation tap to return the text |
| Battery | About 2.3% per hour in Live mode (97% to 93% over a 95-minute run; 3 points in about 78 minutes between readings), so about 40 hours from full with the link always up and normal watch use. The first, 31-minute estimate of 4% per hour was too coarse |

The radio's 40 slots with a gap at slot 7 confirm the rule to read every slot the radio reports. Because offline dictation needs a confirmation tap, the reply flow in M3 should expect it.

On connecting, the watch collected 13 messages that had queued on the radio while it was away, the oldest 7 minutes old, then switched to live delivery, as the Check-in design expects.

Lesson for M2: the spike's first battery log used a Handler delay, whose clock stops while the watch sleeps, so with the screen off it never fired; the radio battery reads, timed by the wall clock on each protocol tick, kept coming every 5 minutes. Anything timed in the background (Check-in intervals, reconnect pauses) must use the wall clock or an alarm, not Handler delays. The same run shows the watch sleeping between radio events, which is good for battery.

**Testing**

- Unit tests on the protocol module using frames captured from a real companion.
- Instrumented tests on a physical watch (the emulator has no real BLE).
- Device matrix: Pixel Watch 5, plus one other brand if available.
- Battery runs per mode: Live and Check-in, 8 hours each, same radio traffic.
- Sideload builds with adb over Wi-Fi during development.

## References

- [MeshCore companion protocol (docs.meshcore.io)](https://docs.meshcore.io/companion_protocol/) — current BLE guide, connection flow, channel commands
- [Companion radio protocol (eastmesh wiki)](https://wiki.eastmesh.au/meshcore/companion-protocol/) — command and response code tables, V3 frames
- [Companion Radio Protocol (MeshCore GitHub wiki, older)](https://github.com/meshcore-dev/MeshCore/wiki/Companion-Radio-Protocol/25369eb5d8b48f37e30681248ac100e2d799c9e6) — message queue and push codes
- [pyMC_core v1.15 notes](https://github.com/pyMC-dev/pyMC_core/pull/72/files) — channel data frames, DFU service note
- [MeshCore firmware](https://github.com/meshcore-dev/MeshCore) — links to meshcore.js and meshcore-cli reference clients
- [MeshCore Open](https://github.com/zjs81/meshcore-open) — open-source Flutter client, useful as a protocol reference
- [Android BLE in the background](https://developer.android.com/develop/connectivity/bluetooth/ble/background) — foreground service and companion device options
- [Standalone Wear OS apps](https://developer.android.com/training/wearables/apps/standalone-apps) — marking the app standalone
