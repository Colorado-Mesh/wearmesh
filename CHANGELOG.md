# Changelog

Each release has a section here, headed with its version; the release workflow publishes that
section as the GitHub Release's notes ([docs/releasing.md](docs/releasing.md)).

## 0.1.0 (not yet released)

The first release, for a few Colorado Mesh testers who sideload. WearMesh is a standalone Wear OS
client for a MeshCore companion radio over Bluetooth, with no phone needed. It is not affiliated
with MeshCore.

- Pairs with a MeshCore BLE companion radio, and remembers more than one.
- Two ways to stay in touch: **Live** holds the link, for messages at once; **Check-in**
  connects every 15, 30 or 60 minutes, for the battery. **Quit** stops both until WearMesh is
  opened again.
- Channels and direct messages, with notifications turned on or off for each conversation.
- Replies by quick replies, dictation or keyboard, from the app or a notification. Direct
  messages show Delivered; a message that fails can be sent again.
- New messages to favourites, recent contacts or channels.
- A message's signal and hops; deleting a direct conversation.
- Optional: keep the contacts you message on the radio (Settings › Keep contacts on radio).
- Two tiles, Messages and Status, and an unread-count complication.
- Sets the radio's clock when it is more than a minute off.
- Settings › About WearMesh: the version, the radio and its firmware, and where the source is.
- The log leaves out message text unless Diagnostics › Message text in log is on, records
  crashes, and Diagnostics › Save log puts a copy where `adb pull /sdcard/Download/WearMesh/`
  fetches it.

Tested on a Pixel Watch 5 with a Heltec Mesh Node T1 on zephcore firmware and a WisMesh Tag on
stock MeshCore companion firmware.
