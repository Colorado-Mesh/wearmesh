# M4 step 2: the Tile

The second part of M4 (plan and decisions in the design doc's M4 plan, 30 October 2026): a WearMesh Tile, one swipe from the watch face. Step 1 was the complication ([M4-step1-complication.md](M4-step1-complication.md)); step 3 is a day's carry with both.

## What it shows

From the top:

- **The unread count**, counted as the complication counts it: unread messages in every conversation whose notifications are on. "Nothing unread" when there are none.
- **The newest two unread messages** (decided for M4), each on two lines: who it is from ("KC0SIQ-05" for a direct message, "Ann · Public" on a channel) and the start of the text. Tapping one opens its conversation, which marks it read.
- **Three buttons** (decided for M4):
  - **Check now** (the arrows): opens WearMesh, which checks with the radio at once (decided for M4: you see the result there). Opening the app normally skips the check if one ended in the last two minutes; Check now does not. In Live, the link already brings messages as they come, so it just opens the app.
  - **Quick reply** (the arrow): opens the quick replies for the conversation of the newest unread message, as a notification's Quick reply does. It is left out when nothing is unread.
  - **Open WearMesh** (the app's icon).
- **The status line**, with the mode and no switch (decided for M4): "Live: Connected" while Live runs, "Check-in · synced 2:14 PM" in Check-in, or "Not synced yet".

The status line gives the clock time of the last sync, not "4 min ago" as the complication does. A Tile cannot count up by itself without being redrawn on a timer, and a timer would cost battery.

## Battery

Like the complication, the Tile never wakes the radio and has no timer. It shows what the last sync stored and updates only when the app asks: after each check-in, each Live collection, Live connecting or stopping, and whenever a conversation is read.

## Testing on the watch

1. **Add it.** Swipe left from the watch face to the end of the Tiles, tap **+**, and choose **WearMesh**. Or long-press a Tile and choose **Add tile**.
2. **Messages.** Have someone send two direct messages, or one direct message and one on a private channel. After the check-in that brings them, the Tile shows "2 unread" and both, newest at the top.
3. **Open one.** Tap a message. WearMesh opens on that conversation. Swipe back to the Tile: the count has gone down and that message is gone.
4. **Quick reply.** With something unread, tap the reply arrow. The quick replies open for the newest message's conversation; tap one and it sends.
5. **Check now.** In Check-in, tap the arrows right after a check-in. WearMesh opens and checks anyway (Home shows it syncing; the log says "Tile: Check now"). The status line on the Tile then shows the new sync time.
6. **Nothing unread.** With everything read, the Tile says "Nothing unread" and shows only Check now and Open WearMesh.
7. **Live.** Switch to Live: the status line shows "Live: Connected", and a message arriving over Live appears on the Tile.
