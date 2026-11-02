# The two tiles

Decided 2 November 2026, after Google's guidance for tiles: one task per tile, glanceable content with a short label, one action along the bottom, and say how fresh the data is. The single Tile of M4 step 2 had a count, messages, three buttons and a status line; it is now two tiles. Both use the current libraries (Tiles 1.4, ProtoLayout 1.2): the action is a compact chip, not yet Material 3 Expressive's curved edge button, which needs newer libraries.

Add either from the watch's tile list: **WearMesh messages** and **WearMesh status**.

## WearMesh messages

| Part | Shows |
| --- | --- |
| Top | "3 unread", or "No new messages": unread messages in conversations that notify |
| Middle | The last messages heard, read or not, from any conversation: two, or three on a watch at least 225 dp wide (Google's breakpoint for larger screens). Unread ones bright with a dot, read ones dimmed. Tap one to open it |
| Under them | How current it is: "Check-in · synced 2:14 PM", or "Live: Connected" |
| Bottom | **Reply**, to the newest unread message in a conversation that notifies (its quick replies), or **Check now** when there is none |

The Open WearMesh button is gone: tapping a message opens the app, and the system shows the app's icon.

## WearMesh status

| Part | Shows |
| --- | --- |
| Top | The mode: "Check-in every 15 min", "Live" or "Quit" |
| Middle | Large: when the watch last reached the radio ("2:14 PM", under "Last synced"), or in Live the link's state ("Connected"). Then, as they apply: "Next 2:29 PM" (the next check-in), "Radio 78% · W0RMT-T1n" (the radio's battery, estimated from its voltage, and its node name), and "Last try failed" when the last sync did not work |
| Bottom | **Check now** |

The radio's battery is the last reading, from Live or a check-in, and is left out once it is more than 6 hours old. The next check-in is the time the alarm was set for; Doze can make it a few minutes late.

## Battery

Neither tile has a timer: times are clock times, and both are redrawn only when the app asks, after each sync, each read, each change of mode, of radio and of Live's link, and when the Check-in alarm is set again after a restart or an update. The Status tile is also redrawn when the radio's battery figure changes, or the radio reports it cannot measure it (the reading is then cleared). Choosing another radio, or forgetting the one in use, clears its battery reading and a failed try, so they never show under another radio. They read what the app has saved and never wake the radio.

## Testing on the watch

1. **Add both.** From the tile list, add WearMesh messages and WearMesh status.
2. **Messages, with something unread.** The bottom chip says Reply and opens the quick replies for that conversation.
3. **Messages, all read.** The chip says Check now, and opens WearMesh, which checks at once.
4. **Status in Check-in.** It shows the mode, the last sync time, the next check-in, and the radio's battery and name. After Check now, the time moves on.
5. **Status in Live.** It shows "Live" and "Connected". After Quit, "Quit".
6. **A failed try.** Turn the radio off and tap Check now: "Last try failed" appears, and goes once a sync works again.
