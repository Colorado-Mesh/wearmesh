# M4: more on the Tile and the complication

Changes made on 31 October 2026, during the M4 carry, to use the room both had to spare. Steps 1 and 2 are [M4-step1-complication.md](M4-step1-complication.md) and [M4-step2-tile.md](M4-step2-tile.md).

## Decided (31 October 2026)

- **The Tile always shows the last two messages heard**, read or not, from any conversation, quiet channels such as Public included. This replaces the newest two unread messages, which left the Tile empty once everything was read.
- **The complication can count one channel**, chosen in WearMesh under Settings › Complication. It isn't chosen per complication on the watch face.
- **A channel complication shows the count over the channel's short name**, or the name alone when nothing in it is unread.

## The Tile

- The two messages are the newest the watch has received, in any conversation. An unread one is bright, with a dot before the sender. A read one is dimmed.
- Tapping one opens its conversation, as before.
- The heading is still the unread count across conversations that notify, as before.
- **Quick reply** still goes to the newest unread message that counts. That may not be one of the two shown, for example when the newest is a Public message. It is left out when nothing that notifies is unread.
- "No messages heard yet" shows before the first sync brings anything.

## The complication

Settings › Complication offers **All that notify** (the default, as in step 1) or any of the radio's channels. The choice is kept per radio.

| Slot | All that notify | A channel |
| --- | --- | --- |
| Short text | The icon and the count; the icon alone at 0 | The count over the short name, such as "3" over "Publ"; the name alone at 0 |
| Long text | "Synced … ago" or "Live: …", titled "3 unread" or "WearMesh" | The same text, titled "3 unread in Public" or "Public" |
| Image only | The icon | The icon |

- A channel complication counts that channel's unread messages whether or not its notifications are on.
- The short name drops a leading "#". A name of up to 5 characters is used whole; a longer one gives its first 4 ("Public" is "Publ", "#testing" is "test").
- The icon is left out of a channel's short text, so faces show the name instead.
- If the chosen slot later holds no channel, the complication goes back to All that notify.

## Testing on the watch

1. **Tile, all read.** With everything read, the Tile still shows the last two messages, dimmed. Tapping one opens it.
2. **Tile, a new message.** After a new message arrives (on Public, say), it shows at the top, bright with a dot. Reading it dims it.
3. **Quick reply.** With only a Public message unread, the reply arrow is missing. A direct message brings it back, and it opens that conversation.
4. **Complication, a channel.** Choose a busy channel in Settings › Complication. The complication shows its unread count over its short name; reading the channel leaves the name alone.
5. **Back to all.** Choose All that notify: the complication returns to the icon and the count.
