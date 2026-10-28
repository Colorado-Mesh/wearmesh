# M2 step 4: notifications and modes

Step 4 of [M2](design.md) makes WearMesh tell you when something arrives, without buzzing for every message on a busy channel, and turns Live and Check-in into one mode setting. It still sends nothing; replies are M3.

## Notifications

- **One notification per conversation**: its name, then its newest unread messages (up to 5) as sender and text. All of them are grouped, and only the group's summary buzzes.
- **Tapping one opens that conversation**; tapping the summary opens Home. Opening a conversation, from a notification or from Home, clears its notification, and the summary with the last one.
- **Which conversations notify** (decided for step 4): direct messages and private channels by default; Public and hashtag channels not. Each can be switched at the bottom of the conversation, or in **Settings › Notifications**, which lists them all.
- **A message naming your radio** (`@[your node name]`) notifies even on a channel that is off.
- **No notifications for a radio's first-sync backlog**: the sync right after choosing or switching to a radio stores what was queued without notifying; once a sync has collected that queue, later ones notify as usual. In Live, notifications come once the radio's queue is empty, so a burst of messages is one update.
- **Nothing notifies for the conversation open on screen.**
- **One buzz at a time per conversation**: a conversation buzzes for its first new message, then updates quietly for 5 minutes unless it is opened. A check-in with messages in several conversations buzzes once.
- **Sound and vibration** follow two Android notification channels, **Direct messages** and **Channels**, which the watch's own notification settings can change. When the watch blocks WearMesh's notifications, Settings shows **Allow notifications**.

## Modes

**Settings › Mode** is Live or Check-in, in place of the separate Check-in and Live switches.

| Mode | What it does |
| --- | --- |
| Live | Holds the link open; messages arrive within seconds. Opening the app starts it if it is not running, after a restart, say; a link that dropped reconnects by itself. About 2.3% battery an hour (M0) |
| Check-in | Syncs every 15, 30 or 60 minutes, also while the watch sleeps; opening the app syncs too. About 1% an hour overnight (step 2) |

A new install starts in **Live**. Once Live has connected, Home offers **Switch to Check-in** or **Not now**, once (decided for M2). A watch that had Check-in on stays in Check-in and is not asked; one that had it off is in Live, and is offered Check-in once. Switching to Live while a check-in is running waits for it to finish, so the two never contend for the radio.

The 5-minute buzz limit is kept in memory, so it starts afresh if Android stops the app between check-ins: at worst, an extra buzz.

## Testing on the watch

1. **Mode.** Settings shows `Mode: Check-in` (Check-in was on). Tap it: `Mode: Live`, and Home's status becomes `Live: Connected`. Tap again to go back.
2. **A direct message.** Have someone send a DM, or DM the watch's radio from the phone app on the test radio. A notification should come at the next check-in (or at once in Live), titled with the sender; tapping it opens the conversation, and the notification goes.
3. **A quiet channel.** A Public message should not notify; one that mentions your radio's name should.
4. **Switching a conversation.** Turn Public on from the bottom of its conversation, then off again in Settings › Notifications; both places should agree.
5. **The backlog.** Switch to the test radio and back: the sync after each switch should store messages without notifying.
6. **On screen.** Keep a conversation open in Live while a message arrives in it: no notification.
7. **The first-run offer** needs a fresh install: `adb uninstall org.coloradomesh.wearmesh`, install, choose your radio. Once Live connects, Home should offer Check-in once. (Uninstalling deletes the stored messages; they refill from the radio.)
8. **Sound.** In the watch's Settings › Apps › WearMesh › Notifications, the two channels **Direct messages** and **Channels** should be listed.
