# M3 step 2: compose

Replying from the watch, as decided for M3 (29 October 2026). Sending itself is step 1 ([M3-step1-sending.md](M3-step1-sending.md)); the **Send test message** chip it added is gone.

## What it does

- **Reply** sits under the newest message in each conversation. It opens straight into dictation, with the keyboard one tap away (decided for M3). Backing out of dictation leaves the reply screen: **Speak**, **Type**, and the quick replies.
- **Dictated or typed text is shown first**, with how many bytes it uses of the limit, then **Send**, **Speak again**, **Type instead** and **Cancel**. Text over the limit cannot be sent; the screen says by how much.
- **A quick reply sends at once**, without a preview (decided for M3), and the screen closes.
- **Replying to one person on a channel:** tap their message, then **Reply to Ann**. The text starts with `@[Ann]`, as the MeshCore apps write it, which notifies them if their app does mentions. Tapping a message in a direct conversation offers **Reply**.
- **Retry:** tap a message of yours that failed, then **Retry**. It is sent again as if just written.
- **A clearer failure.** A direct message the radio refuses, to a node the radio does not list (step 1 found its contact list full and dropping old contacts), shows **Failed: contact not on radio**, and the log says so. Getting the contact back on the radio (an advert, or the phone app) and marking it a favourite there keeps it.
- **Settings › Quick replies:** the starting set is OK, Yes, No, On my way, Copy, Call me, 73. Tap one to **Change** or **Remove** it; **Add** puts a new one at the end (12 at most); **Back to the starting set** undoes all changes. Changing and adding use the keyboard: Wear OS cannot hand its text screen words to edit, so a change is typed afresh.

## Contacts in the log

The radio's contact list is full (about 350) and drops older contacts as it hears new ones, and with them the people being messaged: a MeshCore radio can neither send a direct message to, nor decode one from, a node it does not list. To see whether marking someone a favourite keeps them, each full read now logs the radio's favourites, and any favourite or direct-message contact it no longer lists:

```
Contacts: 350 on the radio, 2 favourites (KC0SIQ-05, W0RMT-M9)
Contacts: the radio no longer lists W0RMT-M9 (d1a23c09f94d), a favourite
```

The favourite mark is bit 0 of the contact's flags on the radio, as the MeshCore apps set it; meshcore-kotlin now reads it (`Contact.isFavourite`), and the store keeps it (schema version 4, migrated from 3). "No favourites" while the phone app shows one would mean the app keeps the mark to itself; a favourite dropped anyway would mean the firmware does not protect favourites when the list is full.

## The byte limit

MeshCore carries 160 bytes of UTF-8 text. On a channel the radio puts its node name and ": " in front inside the same 160, so a radio named `🔥W0RMT-T1n` (14 bytes) leaves 144 for the message; a direct message has all 160. Most emoji take 4 bytes, accented letters 2, and a reply's `@[Name] ` counts too.

## Testing on the watch

1. **Dictation.** In a channel, tap **Reply** and say a sentence. The preview shows it with its byte count; **Send** sends it, and it appears in the conversation as "Sent to radio".
2. **Back out.** Tap **Reply**, then swipe away dictation: the reply screen offers Speak, Type and the quick replies.
3. **Keyboard.** **Type**, write something, and send it from the preview.
4. **A quick reply.** Tap **OK**: it sends at once and the screen closes.
5. **Reply to someone.** Tap another person's message on a channel, **Reply to …**, and send: the phone shows it starting with `@[their name]`.
6. **Too long.** Dictate a long paragraph: the preview says how many bytes over, and Send is off.
7. **Quick replies.** In Settings, change one, add one and remove one; the reply screen shows the new list. **Back to the starting set** restores it.
8. **Retry.** The direct messages that failed in step 1 while the contact was missing are still in that conversation, marked Failed. Tap one and **Retry**: now that the contact is back, it should end as Delivered.
