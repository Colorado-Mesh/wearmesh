# WearMesh

A standalone Wear OS app for [MeshCore](https://docs.meshcore.io/) messaging from the wrist. The watch connects straight to a MeshCore companion radio over Bluetooth, with no phone in the loop, and handles channel and direct messages.

It is a [Colorado Mesh](https://coloradomesh.org/) project. Its protocol and Bluetooth code is shared with [MeshCore TAK Bridge](https://github.com/Colorado-Mesh/meshcore-tak-bridge) through [meshcore-kotlin](https://github.com/Colorado-Mesh/meshcore-kotlin), included here as a git submodule. How it is meant to work, and the plan, are in [docs/design.md](docs/design.md).

## Status

**Milestone M0, the Bluetooth spike.** Not a messaging app yet. The `wear` app pairs with a companion radio, reads its channels and contacts, collects queued messages, and logs everything, so the questions in the design can be answered on a real watch: does the link hold with the screen off, what packet size does it get, and is on-device dictation good enough. It reads from the radio only and sends nothing on the mesh.

The first target watch is the Pixel Watch 5. How to run the M0 tests and what to record is in [docs/M0-spike.md](docs/M0-spike.md).

## Layout

| Folder | What it holds |
| --- | --- |
| `meshcore-kotlin/` | A git submodule: the companion protocol in pure Kotlin, with its tests, and the Bluetooth link, shared with MeshCore TAK Bridge |
| `wear/` | The Wear OS app: the M0 spike screen and its foreground service |
| `docs/` | The design proposal and the M0 test procedure |

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
