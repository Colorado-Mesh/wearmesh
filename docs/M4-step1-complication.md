# M4 step 1: the complication

The first part of M4 (plan and decisions in the design doc's M4 plan, 30 October 2026): a WearMesh complication for the watch face. The Tile is step 2.

## What it shows

| Slot | Shows |
| --- | --- |
| Short text (most faces) | The app's icon and the unread count; the icon alone when nothing is unread (decided for M4) |
| Long text (wide slots) | "Synced 4 min ago", kept current by Wear OS, or "Live: Connected" while Live runs; titled with the unread count |
| Image only | The app's icon |

- **The count** is unread messages in every conversation whose notifications are on (decided for M4), so it matches what buzzes: direct messages and private channels by default, plus any channel turned on in Settings › Notifications. Over 99 shows "99+".
- **Tapping it** opens WearMesh, which syncs on opening, as always.
- **It never wakes the radio.** It shows what the last sync stored, and updates only when the app asks: after each check-in, each Live collection, Live connecting or stopping, and whenever a conversation is read (in the app or by Mark read). Wear OS has no timer for it, so it costs no battery of its own.

## Testing on the watch

1. **Add it.** Long-press the watch face, Customise, pick a complication slot and choose **WearMesh**.
2. **A count.** Have someone send a direct message. After the check-in that brings it, the complication shows 1 (or more).
3. **Read it.** Open the conversation: the count goes back to the icon alone. Mark read on a notification does the same.
4. **A quiet channel.** A Public message (notifications off) does not change the count; a mention of your radio on it still notifies, but counts only if that channel's notifications are on.
5. **Long text.** On a face with a wide slot, the complication shows "Synced … ago", counting up, then "Live: Connected" after switching to Live.
