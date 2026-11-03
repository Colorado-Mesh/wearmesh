# Testing WearMesh

Thank you for trying WearMesh. It is a MeshCore client for Wear OS watches: the watch talks to
your MeshCore companion radio over Bluetooth, with no phone needed, and handles channel and
direct messages from the wrist. It comes from [Colorado Mesh](https://coloradomesh.org/) and is
not affiliated with MeshCore.

This guide is written for someone who has never installed an app with adb. It takes about 15
minutes the first time.

## What you need

- **A Wear OS 4 or later watch**: a Pixel Watch, a Galaxy Watch 6 or later, or another. WearMesh
  has been tested on a Pixel Watch 5 only, so other watches are a big part of what you are
  testing.
- **A MeshCore companion radio on BLE companion firmware** (stock MeshCore or zephcore), already
  set up with the MeshCore phone app or web app: its name, channels and contacts. WearMesh does
  not change radio settings or add channels; it reads them from the radio.
- **Its Bluetooth PIN**, which the watch asks for when it first pairs (it is set in the radio's
  settings, or shown on its screen).
- **A computer** (Linux, macOS or Windows) on the same Wi-Fi as the watch, with Google's
  [platform tools](https://developer.android.com/tools/releases/platform-tools), which include
  `adb`. Unzip them and run the commands below from that folder (on macOS and Linux, as
  `./adb`).

**One device at a time.** A companion radio talks to one app at a time. Disconnect the phone
app from the radio before using WearMesh (in the phone app, disconnect, or turn the phone's
Bluetooth off). If the phone app reconnects, WearMesh cannot.

## Installing

The commands below use version 0.1.0 as the example. For another release, put its number in
place of 0.1.0 wherever it appears.

1. **Download** `wearmesh-0.1.0.apk` and `wearmesh-0.1.0.apk.sha256` from
   [Releases](https://github.com/Colorado-Mesh/wearmesh/releases), and move both into the
   platform-tools folder, where you run `adb`. (Or leave them where they are and give `adb` the
   APK's full path in step 6.) Then check the download, from that folder; it should say `OK`, or
   on Windows print the same value as the `.sha256` file:

   ```bash
   sha256sum -c wearmesh-0.1.0.apk.sha256          # Linux
   shasum -a 256 -c wearmesh-0.1.0.apk.sha256      # macOS
   certutil -hashfile wearmesh-0.1.0.apk SHA256    # Windows
   ```
2. **Show Developer options on the watch**: Settings › System › About › Versions, then tap
   **Build number** seven times. (On a Galaxy Watch it is usually Settings › About watch
   › Software information › Software version.)
3. **Turn on Wireless debugging**: Settings › Developer options › Wireless debugging (on some
   watches, turn on **ADB debugging** first). The watch must be on Wi-Fi, not only Bluetooth.
4. **Pair the computer**: in Wireless debugging, tap **Pair new device**. The watch shows an
   address with a port and a six-digit code. On the computer:

   ```bash
   adb pair 192.168.1.42:37123      # the address and port from the pairing screen
   ```

   and type the code.
5. **Connect**: back on the main Wireless debugging screen, the watch shows its IP address and
   port (a different port from pairing):

   ```bash
   adb connect 192.168.1.42:41235
   adb devices                       # the watch is listed as "device"
   ```

6. **Install**, from the folder with the APK in it:

   ```bash
   adb install -r wearmesh-0.1.0.apk
   ```

7. **Turn Wireless debugging off** again when you are done: it roughly doubles the watch's
   battery use.

Pairing is remembered, so next time steps 5 and 6 are enough. The watch's port changes whenever
Wireless debugging is turned on again.

**Updating** is the same `adb install -r` with the new file: your messages and settings stay.
**Uninstalling** (`adb uninstall org.coloradomesh.wearmesh`, or from the watch's app list)
deletes them.

If you ever built WearMesh yourself, the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`:
releases are signed with a different key. Uninstall your build first (which deletes its data).

## First run

1. Open WearMesh and tap **Find my radio**. Allow **Nearby devices** (Bluetooth) and
   notifications when asked.
2. Tap your radio, and enter its PIN when the watch asks to pair.
3. WearMesh starts in **Live** and reads the radio's channels and contacts. Once it says
   Connected, it offers to switch to **Check-in**, which saves battery.
4. If you choose Check-in and Home says Check-in needs **Alarms & reminders**, tap **Allow
   alarms** and turn it on. Without it, Check-in only syncs when you open the app.

## How it works

| | |
| --- | --- |
| **Live** | Keeps the link to the radio open: messages arrive at once. Uses more battery (about 2% an hour on a Pixel Watch 5) |
| **Check-in** | Connects every 15, 30 or 60 minutes (Settings), collects what the radio has queued, and disconnects (about 1 to 1.5% an hour). Opening the app also syncs, and Check now syncs at once |
| **Quit** | Settings › Quit WearMesh stops both until you open the app again |
| **Notifications** | On for direct messages, private channels and messages that mention your radio's name; turn others on in Settings › Notifications |
| **Replying** | Reply in a conversation, or Reply or Quick reply on a notification: quick replies (editable in Settings), dictation, or the keyboard |
| **Tiles** | WearMesh messages (unread messages, Reply or Check now) and WearMesh status (the mode, last and next sync, the radio's battery). Add them from the watch's tile list |
| **Complication** | An unread count for a watch face, for all notifying conversations or one channel (Settings › Complication) |

## What to test

Use it as you normally would on the mesh, and tell us what does not work, what is confusing,
and what works well. Some things we especially want to hear about:

- **Your watch.** Does pairing work? Do check-ins run on time with the screen off (Settings ›
  Diagnostics shows the log)? Galaxy and other watches may manage battery differently from a
  Pixel.
- **Your radio and firmware.** Do channels, contacts and messages all come through? Do direct
  messages show Delivered?
- **Battery.** Roughly how much an hour in Live and in Check-in, with Wireless debugging off.
- **Notifications, replies, tiles and the complication.**

## Reporting

Open an issue on [GitHub](https://github.com/Colorado-Mesh/wearmesh/issues/new/choose):
**Problem** for something that went wrong, **Works on my watch** to say what you tested it on and
how it went. Settings › About WearMesh shows the version, radio model and firmware the forms ask
for.

### Getting the log

The log says what WearMesh did and when. It is the most useful thing to attach.

1. On the watch: Settings › Diagnostics › **Save log**. It says the file's name.
2. With adb connected (Installing, step 5):

   ```bash
   adb pull /sdcard/Download/WearMesh/
   ```

   which copies every saved log into a `WearMesh` folder on the computer.
3. Attach the file to the issue.

**What the log holds:** times, what was synced and sent, the names of your radio, channels and
contacts, and errors. While Diagnostics › Message text in log is off, which it is unless you turn
it on (only do that if asked), messages are logged without their text. Lines written while it was
on keep their text, even after you turn it off, and Save log copies them. So if you ever turned
it on, or ran a WearMesh from before 0.1.0, first turn **Message text in log** off, then tap
**Clear log**, then reproduce the problem you report: the log then holds only that, without
message text. Clear log also helps when the log is long.

## Known limits

- **One device at a time** on the radio, as above.
- **Contacts and channels come from the radio.** WearMesh does not add contacts, change channels
  or set up the radio. Settings › Keep contacts on radio (off by default) makes the people you
  message favourites on the radio and puts them back if it drops them.
- **Messages are short:** up to 160 bytes for a direct message, less on a channel, where the
  radio adds its name. The reply screen shows what is left.
- **Room servers** and other non-text posts are not shown.
- **History** is the last 100 messages of each conversation, kept on the watch only.
- **The first sync after choosing a radio** stores what was waiting on it without notifying.
- **Quick replies on a notification** are under its Quick reply button: the Pixel Watch does not
  show quick replies offered by a notification's own reply action.
- **Pixel Watch 5 only, so far.** Anything on other watches is new ground.
