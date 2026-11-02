# Stock MeshCore firmware

WearMesh was built and tested against zephcore on the Heltec Mesh Node T1. The first testers are likely to run stock MeshCore companion firmware, so on 31 October 2026 it was run for a day against a second radio with stock firmware, to find any differences before they did.

**Radio:** RAK WisMesh Tag, MeshCore companion firmware v1.18.0-81dbae5 (15 Oct 2026), 40 channel slots, 13 programmed, 21 to 26 contacts. Live mode, 06:15 to 19:28, app versions 16 and 17.

| Check | Result |
| --- | --- |
| 1. Pair | Worked; the radio's name ends "(paired)" |
| 2. First sync | Contacts and the 13 channels read. No favourites were set on the Tag, so favourites on stock firmware are untested |
| 3. Clock | The Tag's clock was within a minute of the watch's, so it was not set; setting a reset clock was seen on the T1 the same evening |
| 4. Receive | Channel and direct messages all day; 12 notifications. A Check-in full read before switching to Live worked |
| 5. Send | Channel messages on 6 channels; two direct messages Delivered, acknowledged in 546 and 772 ms. A direct message to a contact the radio does not list was not tried |
| 6. Notification actions | Direct-message replies went a few seconds after their notifications |
| 7. Overnight | Not run on the Tag |
| 8. Switch back | The T1 kept its history and synced normally |

The link held for 13 hours with no drops, and the radio answered every start-up command at once. Nothing in the log points to a difference from zephcore.

## Contacts, 1 November 2026

Run on the same Tag with Settings › Keep contacts on radio on (app version 21, a fresh install), to try what 31 October left open. Every check passed.

| Check | Result |
| --- | --- |
| Clock | The Tag's clock was 79,967 s (22 h) behind after the reinstall; the watch set it, and later connections found it 6 to 7 s ahead, within the minute that is left alone |
| Favourites | After direct messages with 4 people, the next full read made each a favourite (protocol command 9). The Tag took all 4: the following read listed them as favourites (bit 0 of the contact's flags) |
| Put back | With one of them deleted from the Tag in the phone app, the next full read found it missing and put it back, as a favourite; this was done twice |
| Refusal (error 2) | With the contact deleted again and only a brief read, a direct message to it was refused with error 2. The watch put the contact back and sent the message again, which the Tag then flooded to the mesh. It ended Not confirmed: that node did not acknowledge an earlier message either, so it was likely out of range |

## A night in Check-in, 1 to 2 November 2026

The Tag in Check-in every 15 minutes, 18:44 to 07:00, wireless debugging off. Its contact list was full by the evening's first full read (346, then 350, with 7 to 8 favourites), as the T1's is.

| | Result |
| --- | --- |
| Check-ins | 58, none failed: 50 scheduled, never more than 15.3 minutes apart, mostly with the screen off in Doze. Four needed a second connection try (status 133) |
| Time | 7.4 s on average; full reads of the 350 contacts 21 to 24 s, the longest 27.5 s (a brief read whose connection needed 3 tries) |
| Messages | 171 collected, 3 notifications |
| Contacts | Keep contacts on radio made one person you message a favourite again; no one you message was dropped overnight |
| Battery | 100% at 19:05 to 88% at 06:45: about 1.0% an hour, 0.9% from 21:29 to 05:15. The T1's night (M4 step 3) was 0.8 to 1.0% |

Every check on stock firmware has now passed.
