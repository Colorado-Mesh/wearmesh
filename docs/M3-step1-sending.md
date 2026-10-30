# M3 step 1: outbox and sending

M3 lets the watch send (plan and decisions in the design doc's M3 plan, 29 October 2026). This first step does the sending itself, with a **Send test message** chip in each conversation to prove it on the watch; the compose screens come in step 2.

## What it does

- **Sent messages are stored in their conversation** (schema version 3), with a status shown under each: Sending…, Sent to radio, Delivered, Not confirmed or Failed. A sent message counts towards the conversation's 100 and is never unread. Home previews it as "You: …".
- **The update keeps the stored history.** Version 3 adds two columns to the messages table by a migration, where version 2 started the store afresh. `MigrationTest` builds a version 2 database from `wear/schemas/…/2.json`, migrates it, and checks the messages are kept.
- **How it goes:**
  - In **Live**, over the open link, at once. A link still starting sends once start-up has collected the radio's queue.
  - In **Check-in**, over a connection opened at once (decided for M3). The check-in collects the queue as usual, then sends and stays connected until each message has ended, up to 200 s: a direct message is waited on through all 3 tries (decided for M3). A message written while a check-in is already running gets a run of its own straight after.
  - **Every connection also sends what is still waiting**, so a message written with the radio out of reach goes with the next check-in or reconnect. One still unsent after 30 minutes fails instead: news that late could mislead. A waiting message is never trimmed from its conversation, however many arrive after it.
  - **A channel message waiting names only its slot**, so a check-in that sends one reads the channel list first, as Live always does. If the slot has been given another channel in the meantime (from the phone app, say), its waiting messages are dropped with the rest of that channel's history and the log says how many, rather than going to the wrong channel.
- **What each status means:**
  - **Sent to radio** (channels): the radio took it. MeshCore reports nothing more for channel messages, so the watch never says Delivered for one (as the design says).
  - **Delivered** (direct messages): the recipient acknowledged it.
  - **Not confirmed**: no acknowledgement after every try, or the connection ended first. It may still have arrived.
  - **Failed**: the radio would not send it (for a direct message, usually a contact the radio does not have), or it waited 30 minutes.
- **A connection ending part-way.** A channel message the radio had not yet taken waits for the next connection. A direct message already handed over is recorded as not confirmed, since it may have gone out.
- **The radio's clock** (decided for M3). Each connection reads it after the device query and sets it from the watch's when they differ by more than 60 s. This uses a new opt-in in the shared meshcore-kotlin code (`StartUp.clockTolerance`), which MeshCore TAK Bridge does not use. Some firmware refuses to move its clock back; the log says so and the connection carries on.

## The log

```
Radio clock 312 s behind the watch; setting it
Check-in radio: Set the radio's clock to this device's; it was 312 s behind
Check-in: Outbox: Message on channel 3 sent
Check-in: Outbox: Direct message to a1b2c3d4e5f6 delivered
Check-in: Outbox: Direct message to 0000000000ff failed: the radio would not send it (is the contact on the radio?)
```

A clock within 5 s of the watch's logs nothing.

## Testing on the watch

1. **A channel, in Check-in.** Open a channel you can watch from a phone on another radio, such as #testing, and tap **Send test message**. The message appears at the bottom as "You · Sending…", then "Sent to radio" within a few seconds; the phone should show "WearMesh test 18:42:05" from your radio's name.
2. **A direct message, in Check-in.** Open a direct-message conversation with someone who can answer, or with the phone on another radio, and tap **Send test message**. It should end as **Delivered**, or **Not confirmed** after all 3 tries if the other radio is out of reach (that can take a few minutes).
3. **The same in Live.** Switch to Live and repeat 1 and 2: each should go at once.
4. **Out of reach.** Turn the radio off, send a test, and turn the radio on again within 30 minutes: the message stays "Sending…" and goes with the next check-in (or reconnect in Live).
5. **The clock.** When the radio's clock is more than 5 s off, the log shows a `Radio clock …` line, and when it is more than 60 s off the watch sets it. A clock within 5 s logs nothing, so no line is also a pass.
6. **The update.** Install over the current version: the stored messages should all still be there.
