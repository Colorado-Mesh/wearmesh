# M3 step 3: reply from notifications

Replying without opening the app, as decided for M3 (29 October 2026): **Reply** on every message notification. Compose in the app is step 2 ([M3-step2-compose.md](M3-step2-compose.md)).

## What it does

- **Reply** on each conversation's notification is shown by the watch as emoji, voice and keyboard icons. The quick replies are passed with it too, but the Pixel Watch does not show them, on the notification or in its keyboard (found in the M3 field test, 30 October).
- **Quick reply** opens WearMesh on that conversation's quick replies (as set in Settings › Quick replies), without dictation first: one tap sends, and the conversation shows it. It takes the notification down. The reply goes to the conversation: on a channel it carries no `@[name]`; tapping someone's message in the app does that.
- **Sending works as in the app:** over Live's link, or in Check-in mode over a connection opened at once, which Android allows for a short while after a tap on a notification.
- **Replying marks the conversation read** and takes its notification down; **Mark read** does the same without replying.
- **A reply over the byte limit** is not sent. A notification says so ("Not sent to #testing: Too long by 12 bytes") with the text, since the reply screen has no room to show and edit it; tapping it opens the conversation.
- **A message that fails notifies** (decided for M3): "Not sent to Ann", with the reason ("Your radio does not have this contact", or why the radio refused it, or that it waited 30 minutes) and **Retry**. Tapping Retry, there or on the message in the app, sends it again, and takes the notification down once the message is waiting to send. Each failed message and each rejected reply has a notification of its own, so a second does not replace the first. A direct message that is only **Not confirmed** (it may have arrived) does not notify.

## Testing on the watch

1. **A quick reply.** Have someone message a channel or you (in Check-in, wait for the check-in that brings it). On the notification tap **Reply**, then **OK**: the notification goes, and the conversation shows "You: OK · Sent to radio" (or Delivered for a direct message).
2. **Voice.** **Reply**, speak a sentence, confirm: the same.
3. **Live.** Repeat 1 in Live: it should go at once.
4. **Mark read.** On another notification tap **Mark read**: it goes, and the conversation's unread count on Home is cleared.
5. **Too long.** **Reply** with the keyboard and type more than fits: a "Not sent" notification with the reason and the text.
6. **A failure.** Send a direct message to a contact your radio does not have (or reuse an old one): "Not sent to …" with **Retry**. Tap Retry once the contact is back: it goes, and the notification goes too.
