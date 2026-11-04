# WearMesh on Google Play

M5 step 4 (Play internal testing): what goes into the Play Console, kept here so the listing
and the answers to Play's forms change with the app. Set up on 4 November 2026: a personal
developer account, the app `org.coloradomesh.wearmesh` signed with the release key (Play App
Signing given that key, so Play and GitHub builds update each other), and the Wear OS form
factor with its own tracks ("Wear OS only").

## Store listing

**App name** (30 characters at most): `WearMesh`

**Short description** (80 at most):

> MeshCore messages on your Wear OS watch, straight from your companion radio.

**Full description** (4,000 at most):

> WearMesh puts MeshCore messaging on your Wear OS watch. The watch talks straight to your MeshCore companion radio over Bluetooth, with no phone needed, so you can read and answer channel and direct messages from your wrist.
>
> • Live mode holds the link to your radio, for messages the moment they arrive.
> • Check-in mode connects every 15, 30 or 60 minutes and saves battery.
> • Notifications for direct messages, private channels and mentions, set for each conversation.
> • Reply with quick replies, dictation or the keyboard, from the app or a notification. Direct messages show when they are delivered.
> • Start a message to a favourite, a recent contact or a channel.
> • Two tiles: your newest messages with Reply, and the link's status with Check now.
> • A complication with your unread count: across the conversations that notify you, or for one channel you choose.
> • A message's signal strength and hop count.
>
> You need a MeshCore companion radio on Bluetooth (BLE) companion firmware, set up with the MeshCore app first: WearMesh reads the radio's channels and contacts, and does not change its settings. A radio talks to one device at a time, so disconnect the phone app first.
>
> WearMesh collects no data and has no Internet access: your messages stay on your watch and your radio.
>
> WearMesh is free software (GNU GPL 3.0) from Colorado Mesh, a community mesh network group. It is not affiliated with MeshCore. Source and issues: github.com/Colorado-Mesh/wearmesh

**Graphics**

| Asset | File |
| --- | --- |
| App icon, 512 × 512 | `docs/icon/icon-512.png` |
| Feature graphic, 1024 × 500 | `docs/icon/feature-graphic.png` (the social preview, scaled) |
| Wear OS screenshots, square, at least 384 × 384, the app's screen only (no watch frame) | Taken from the watch: `adb exec-out screencap -p > home.png`. Suggested: Home, a conversation, the reply screen, a notification, the Messages tile, the Status tile |

Play asks for phone screenshots too on some listings; WearMesh is Wear OS only, so leave the
phone and tablet sections empty unless the Console refuses to save without them.

**Category:** Communication. **Contact:** email `w0rmt@coloradomesh.org`, website
`https://coloradomesh.org`. **Privacy policy:** `https://coloradomesh.org/wearmesh/privacy`
([privacy.md](privacy.md)).

## App content (Policy › App content)

| Form | Answer |
| --- | --- |
| Privacy policy | The URL above |
| Ads | No, the app has no ads |
| App access | All or some functionality is restricted. Instructions: "WearMesh needs a MeshCore companion radio (Bluetooth LE companion firmware, such as a Heltec or RAK device) to connect to; there is no login. Without one, the app opens on its welcome screen and Find my radio finds nothing. A video of pairing, syncing, sending and receiving is linked in the foreground service declaration." |
| Content rating | The questionnaire: pick the category closest to a messaging app. No violence, sexual content, profanity, drugs, gambling. Users can interact and exchange messages: Yes (over the user's own radio; no moderation, since nothing passes through a server). Shares location: No. Digital purchases: No |
| Target audience | 18 and over (not directed at children; avoids the Families policy) |
| News app | No |
| Data safety | Collects or shares user data: **No**. Everything stays on the watch; nothing is sent to the developer or third parties, and the app has no Internet permission. Messages the user sends go to the user's own radio and from it over the mesh, at the user's request. No account, so no account deletion |
| Government app, financial features, health | No |
| Advertising ID | No, the app does not use it |
| Foreground service permissions | `FOREGROUND_SERVICE_CONNECTED_DEVICE`: "Keeps the Bluetooth link to the user's MeshCore companion radio while Live mode is on, and while each check-in runs (usually a few seconds), so messages arrive and send while the watch screen is off. Started by the user choosing a mode; Quit stops it." Needs a video link (an unlisted YouTube video or a shared Drive file) showing pairing and Live |
| Exact alarm | Uses `SCHEDULE_EXACT_ALARM`, which the user grants; no declaration is needed for it (only `USE_EXACT_ALARM` needs one) |

## Releases

The AAB from the Release workflow's run (artifact `wearmesh-<version>-aab`) goes on the Wear OS
only tracks: Test and release › Testing › Internal testing, with "Wear OS only" selected. Its
release notes are the version's CHANGELOG.md section, shortened to 500 characters.
