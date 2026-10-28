# WearMesh

<p align="center"><img src="docs/icon/icon-512.png" width="160" alt="The WearMesh icon: the Colorado Mesh mark of snow-capped mountains and green hills, inside a round red-to-gold watch bezel with a glowing mesh node at 12 o'clock and a crown at 3"></p>

A standalone Wear OS app for [MeshCore](https://docs.meshcore.io/) messaging from the wrist. The watch connects straight to a MeshCore companion radio over Bluetooth, with no phone in the loop, and handles channel and direct messages.

It is a [Colorado Mesh](https://coloradomesh.org/) project. Its protocol and Bluetooth code is shared with [MeshCore TAK Bridge](https://github.com/Colorado-Mesh/meshcore-tak-bridge) through [meshcore-kotlin](https://github.com/Colorado-Mesh/meshcore-kotlin), included here as a git submodule. How it is meant to work, and the plan, are in [docs/design.md](docs/design.md).

## Status

**M2 step 3: screens.** A read-only MeshCore client on the watch. M0 (the Bluetooth spike) and M1 (the shared meshcore-kotlin code) are done; M2 has shown a background Check-in reaching the radio every 15 minutes all day and stored what it sends. The `wear` app now has its own screens: conversations with unread counts, a conversation view, radios, settings and diagnostics. It reads from the radio only and sends nothing on the mesh; replies are M3.

How to run the tests and what to record: [docs/M0-spike.md](docs/M0-spike.md) for the Live link, [docs/M2-step1-check-in.md](docs/M2-step1-check-in.md) for Check-in, [docs/M2-step2-storage.md](docs/M2-step2-storage.md) for storage, [docs/M2-step3-screens.md](docs/M2-step3-screens.md) for the screens. The M2 plan is in [docs/design.md](docs/design.md).

## Layout

| Folder | What it holds |
| --- | --- |
| `meshcore-kotlin/` | A git submodule: the companion protocol in pure Kotlin, with its tests, and the Bluetooth link, shared with MeshCore TAK Bridge |
| `wear/` | The Wear OS app: Live and Check-in, in `data/` the message store and the sync step shared by both, and in `ui/` the screens |
| `wear/schemas/` | The database schema of each version, written by Room during the build |
| `docs/` | The design proposal and the test procedures; `docs/icon/` has the icon and the script that draws it (`python3 docs/icon/make_icon.py` after changing it), plus `social-preview.png` for GitHub |

## Build

The shared code is a submodule, so clone with it:

```bash
git clone --recurse-submodules https://github.com/Colorado-Mesh/wearmesh.git
# or, in a clone made without it:
git submodule update --init
```

Then open the folder in Android Studio, or from a terminal:

```bash
./gradlew :wear:assembleDebug                    # wear/build/outputs/apk/debug/wear-debug.apk
./gradlew :wear:testDebugUnitTest                # the app's unit tests, the database's on Robolectric
./gradlew -p meshcore-kotlin :protocol:test      # the shared protocol's unit tests
```

To move to a newer version of the shared code: `git -C meshcore-kotlin pull origin main`, then commit the submodule change.

Install on a watch with wireless debugging on (Settings, Developer options, Wireless debugging):

```bash
adb pair <watch-ip>:<pairing-port>
adb connect <watch-ip>:<port>
adb install -r wear/build/outputs/apk/debug/wear-debug.apk
```

Needs JDK 17 and the Android SDK (compile SDK 35). The app runs on Wear OS 4 (API 33) and later.

## License

GPL-3.0, the same as MeshCore TAK Bridge. See [LICENSE](LICENSE).
