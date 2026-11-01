# Keeping the people you message on the radio

Decided 1 November 2026, after the M4 nights showed the T1 dropping direct-message contacts again.

## The problem

A MeshCore companion radio adds every node it hears an advert from, until its contact list is full (about 350). With **Overwrite Oldest** on, as on the T1, it then drops the oldest contact that isn't a favourite to make room; with it off, it stops adding. A radio that no longer lists someone can neither send them a direct message (it refuses with error 2, shown as "Failed: contact not on radio") nor read one from them, until they advert again.

## What the watch does

- **The people you message become favourites on the radio.** After each full read of the radio's contact list (hourly in Check-in, on each connection in Live), every contact with a direct-message conversation that is not yet a favourite is saved back to the radio with the favourite flag set, keeping its route and position as the radio gave them. The radio keeps favourites when it makes room.
- **Dropped ones are put back.** The watch keeps the key, name and type of every contact you have messaged, even after the radio drops it. After a full read, any of them the radio no longer lists is saved back to it, as a favourite, with no known route: the first message floods, and the reply teaches the radio a path.
- **A refused direct message is sent once more.** If the radio refuses a direct message because it no longer lists the recipient, the watch puts the contact back and sends the message again, once. A second refusal ends as before, "Failed: contact not on radio".
- A check-in that saves contacts waits up to 15 s for the radio's answers before disconnecting.

Saving uses the companion protocol's add-or-update-contact command (code 9), which meshcore-kotlin now sends (`CompanionClient.saveContact`); it lays a contact out as the radio lists it.

## In the log

```
Contacts: the radio no longer lists W0RMT-M9 (d1a23c09f94d), a contact with a direct-message conversation
Contacts: putting W0RMT-M9 (d1a23c09f94d) back on the radio, as a favourite
Contacts: making 🔥 W0RMT 02 (b64e547e2ba7) a favourite on the radio, as someone you message
Outbox: the radio no longer listed W0RMT-M9; putting them back, as a favourite, and sending again
Contacts: the radio did not take W0RMT-M9 (error 3); if its list is full, set it to overwrite the oldest, or to add only chat users
```

## On the radio, from the phone app

These settings keep the list from filling with nodes you never message, and need nothing from the watch:

- **Auto-add:** chat users only, or manual, rather than every repeater, room server and sensor heard.
- **Overwrite Oldest:** on, so a full radio makes room rather than refusing new contacts, including the ones the watch puts back.

## Testing on the watch

1. **Favourites.** After the next full read (Settings › Read channels and contacts again), the log makes each person you have messaged a favourite, and the phone app shows them as favourites.
2. **A drop.** When the log next says the radio no longer lists someone you message, the next lines put them back. A direct message to them is Delivered.
3. **A refusal.** Send a direct message to someone the radio has just dropped, before the next full read: the log shows them put back and the message sent again, and it ends Delivered or Not confirmed rather than Failed.
4. **Stock firmware.** The same on the WisMesh Tag, where favourites and error 2 are still untested.
