# WearMesh privacy policy

Effective 4 November 2026. This is the policy for WearMesh, a Wear OS app from
[Colorado Mesh](https://coloradomesh.org/), published on Google Play by Bud Talbot (W0RMT). It is meant to be hosted at coloradomesh.org/wearmesh/privacy; the copy in the
[WearMesh repository](https://github.com/Colorado-Mesh/wearmesh/blob/main/docs/privacy.md) is
the same.

## In short

WearMesh collects nothing. It has no Internet access, no accounts, no analytics, no ads and no
crash reporting service. Everything it keeps stays on your watch. Neither Colorado Mesh nor
anyone else receives any of it from the app.

## What WearMesh does with your data

- **Your radio.** WearMesh connects over Bluetooth to the MeshCore companion radio you choose,
  and to nothing else. It reads your radio's name, settings summary (model, firmware, battery),
  channels and contacts from it, and the messages it has received.
- **Messages.** Messages your radio received, and messages you write, are stored on the watch,
  up to the last 100 of each conversation, so you can read them. They are deleted when you
  delete a direct conversation, forget the radio, or uninstall the app. Android's backup is turned
  off for WearMesh, so they are not copied to your Google account.
- **Sending.** A message you send goes from the watch to your radio, which transmits it over
  the MeshCore radio network. Messages on a mesh can be received by other people's radios: on a
  public channel, anyone listening; on a private channel or as a direct message, its members or
  recipient. That is how MeshCore works and is between you and the mesh; WearMesh adds nothing
  to what is sent.
- **Your radio's contacts.** Only if you turn on Settings › Keep contacts on radio, WearMesh
  saves the contacts you message to your radio, as favourites. It changes nothing else on the
  radio, except setting its clock when it is more than a minute off.
- **Settings and quick replies** are stored on the watch.
- **The log.** WearMesh keeps a log of what it did (connections, syncs, sends, errors, and the
  names of your radio, channels and contacts) on the watch, to help find problems. It leaves out
  the text of messages unless you turn on Diagnostics › Message text in log. It stays on the
  watch unless you tap Diagnostics › Save log, which puts a copy in the watch's Download folder,
  for you to copy off with a computer and share if you choose. Clear log empties it.
- **Dictation and the keyboard** are your watch's own system services, provided by Google or
  your watch's maker under their own privacy policies. WearMesh receives only the text you
  approve.

## Permissions

| Permission | Why |
| --- | --- |
| Nearby devices (Bluetooth scan and connect) | To find and connect to your radio. Scanning is never used to work out where you are |
| Notifications | To tell you about new messages and messages that could not be sent |
| Alarms & reminders | So Check-in mode can sync on schedule while the watch sleeps |
| Foreground service (connected device), wake lock, run at startup | To keep the link to your radio while Live or a check-in runs, and to set the next check-in after a restart |

WearMesh does not ask for location, contacts, microphone (dictation is the system's), camera,
or Internet access.

## Children

WearMesh is not directed at children under 13.

## Changes

If this policy changes, the new version is posted here and in the repository, with its date.

## Contact

Bud Talbot, Colorado Mesh: [w0rmt@coloradomesh.org](mailto:w0rmt@coloradomesh.org), or an issue
on [GitHub](https://github.com/Colorado-Mesh/wearmesh/issues).
