# M2 step 2: storage and sync

Step 2 of [M2](design.md) keeps what the radio sends on the watch. The screens that show it are step 3; for now the spike screen shows how much is stored, and the log shows what each sync did.

## What is stored

A Room database on the watch (`wearmesh.db`), holding data from one radio:

| Table | What it holds |
| --- | --- |
| `radio` | The radio's address, the contact list time to ask for changes from, and when channels and contacts were last read in full |
| `channels` | Each programmed slot's name and kind (Public, hashtag, private). Never channel keys |
| `contacts` | Public key, the 12-character prefix direct messages carry, name, type, last advert |
| `messages` | Conversation (`c:<slot>` for a channel, `d:<prefix>` for a direct message), sender, text, the sender's sent time, the watch's received time |

Decided for M2:

- **One history.** Syncing with a different radio clears everything first; the new radio's queue and a full read fill it again.
- **Forget deletes messages.** The store can forget a radio already; the button comes with the radios screen in step 3.
- **100 messages per conversation**, the most recently received. Sender clocks can be wrong, so "newest" means last stored, not latest sent time.
- **Sender and text split** from MeshCore's `Name: text` form. A prefix longer than a node name (31 bytes), or one with a line break, is kept as part of the text.
- **No duplicates.** A message with the same conversation, sender, sent time and text as one already stored is dropped and logged (`Already stored, not kept twice`). The same text sent again later has a new sent time and is kept.
- A channel slot that now holds a different channel, or none, loses its messages. A slot the radio did not answer for in time keeps what was stored, and the log names it.

## How a sync reads the radio

Check-in and Live share one sync step. Each connection is a **full read** or a **brief read**:

| | Full read | Brief read |
| --- | --- | --- |
| Channel slots | All 40 | None; the stored list is used |
| Contacts | The whole list (about 350); contacts the radio no longer has are dropped | Only those changed since the last read |
| When | Live, each time the link comes up; **Check now**; a radio the store does not know; a check-in 24 hours or more after the last complete full read | Every other scheduled check-in |

A full read counts as complete only when the whole contact list came and every channel slot was answered; otherwise the next check-in reads everything again.

In night 2 of step 1, a check-in with nothing new still took about 8.5 s, about 7 of them after the link was up, mostly reading slots and contacts. A brief read skips that.

Check-in now tries to connect 4 times instead of 3 (one night-2 check-in needed all 3), and the in-alarm fallback's limit is 25 s, down from 40, to leave room for the store within the minute a broadcast is given.

Not in this step: syncing when the app is opened (decided for M2) comes with the screens in step 3.

## Testing on the watch

1. Install the build. In the log, **Check now** ends with a line like:

   ```
   Check-in: done in 9.5 s (link up after 1.8 s, full read), 12 new messages, 0 already stored; watch battery 93%
   ```

   The screen's facts show `Stored: N messages in M conversations, 40 channels, 351 contacts` (your counts).
2. Leave Check-in on. Scheduled check-ins should say `brief read` and, with nothing new, take noticeably less than night 2's 8.5 s.
3. Run **Check now** twice in a row. The second should add 0 messages, and the counts should not change.
4. Let it run overnight as in step 1 and pull the log the same way:

   ```bash
   adb shell run-as org.coloradomesh.wearmesh cat files/m0-log.txt > checkin-log-3.txt
   ```

| Question | Where to look |
| --- | --- |
| Are brief check-ins shorter? | `done in N s` on `brief read` lines with 0 new messages, against about 8.5 s in night 2 |
| One full read a day? | `full read` lines: Check now, then one about 24 hours later |
| Any slot left unanswered? | `No answer for channel slot(s)` lines |
| Do duplicates still come? | `Already stored, not kept twice` lines, with the conversation and sent time |
| Did the store keep up? | No `Storage failed` or `store did not catch up` lines |
| Battery | `watch battery` at the start and end, against night 2's 0.9% an hour |

To look at the database itself, copy it off the watch (the `-wal` file holds recent writes) and open it with `sqlite3`:

```bash
for f in wearmesh.db wearmesh.db-wal wearmesh.db-shm; do
  adb exec-out run-as org.coloradomesh.wearmesh cat databases/$f > $f
done
sqlite3 wearmesh.db "SELECT conversation, sender, body FROM messages ORDER BY id DESC LIMIT 20;"
```

## Result: 24 hours worn (27 to 28 October 2026)

From 08:41 to 08:44 the next day, on the wrist through the day and on the table overnight, with no charging. Every check-in ran; brief reads were about 3 times faster than night 2's check-ins.

| Question | Result |
| --- | --- |
| Check-ins | 97 of 97 scheduled, 14.3 to 15.8 minutes apart; 93 in Doze |
| Brief reads | 94; median 2.8 s, longest 8.3 s. With nothing new: median 2.3 s, against about 8.5 s in night 2 |
| Full reads | Check now at 08:36 and 08:38, then the first scheduled one 24 hours later at 08:44 (9.9 s) |
| Messages stored | 443, with 0 duplicates and no storage errors |
| Unanswered channel slots | None |
| Failures | 2, at 17:28 and 18:28 (below); the next check-in worked each time |
| Watch battery | 80% to 41% in 24 hours, about 1.6% an hour overall: 2.2% an hour in the day while worn and used, 1.0% an hour on the quiet night (23:13 to 05:29), the same as night 2 |

Night-time drain did not fall with check-ins about 6 s shorter, so the check-ins are a small part of it; the watch's own idle drain is most of it. A worn day still leaves the watch at about 40% after 24 hours.

No message came twice this time. Night 2's doubles may have been the same text sent twice with different times, which the store keeps as two messages by design.

**The two failures** were alike: the link connected and found the service, the radio did not answer the packet size request for 4 s, and then the link dropped with status 147 (Android's connection timeout). A drop at that stage ends the check-in, since only a failure before connecting is tried again. Treating a drop during set-up as one more connection try would likely have saved both. That they came exactly an hour apart, at :28, may be chance or the radio being busy with something on the hour; the next long run will show.

## Schema file

Room writes the database schema to `wear/schemas/` during the build. Commit `wear/schemas/org.coloradomesh.wearmesh.data.WearMeshDatabase/1.json` after the first build, so later versions can be checked against it. Until the first release, a schema change starts the store afresh instead of migrating it.
