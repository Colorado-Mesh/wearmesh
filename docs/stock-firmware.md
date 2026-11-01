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

The link held for 13 hours with no drops, and the radio answered every start-up command at once. Nothing in the log points to a difference from zephcore. Still to try on stock firmware: favourites (bit 0 of the contact's flags), the "contact not on radio" refusal (error 2), and a night in Check-in.
