# Releasing WearMesh

M5 step 1 (decided 30 October 2026, in the sharing and release plan): one release key for the
APKs on GitHub Releases and for Google Play, kept by Bud with a backup held by another Colorado
Mesh developer; version names 0.1.0, 0.2.0 and so on; and API 35, which Google Play needs.

## Versions

| | |
| --- | --- |
| `versionName` | `0.1.0`, `0.2.0`, … for releases. Between releases it is the next one with `-dev`, such as `0.1.0-dev`, so a build from `main` never passes for a release |
| `versionCode` | Counts up by one for every build installed on a watch, release or not (25 for 0.1.0-dev, as of M5 step 2). Android will not install a lower code over a higher one, and Play needs each upload higher than the last |

Both are in `wear/build.gradle.kts`. Versions before 1.0 are published as pre-releases.

## The release key, once

The key never goes in the repository (`.gitignore` refuses `*.jks`, `*.keystore`, `*.p12` and
`keystore.b64`), and never anywhere but your machine, its backup and GitHub's secrets.

1. **Make it** on your own computer, outside the repository:

   ```bash
   mkdir -p ~/keys && chmod 700 ~/keys
   keytool -genkeypair -v -keystore ~/keys/wearmesh-release.jks -storetype PKCS12 \
     -alias wearmesh -keyalg RSA -keysize 4096 -validity 10000 \
     -dname "CN=Bud Talbot, O=Colorado Mesh, C=US"
   ```

   It asks for a password; a PKCS12 keystore uses the same password for the key. Valid for about
   27 years, past the end of 2033, as Play requires. Losing it means the app can never be updated
   under the same name, so:

2. **Back it up.** Give the other Colorado Mesh developer the `.jks` file and its password, by
   separate routes (the file one way, the password another). Keep your own copy outside the
   computer too.

3. **Put it on GitHub.** In the repository's Settings › Environments, make an environment named
   `release`. Under its deployment branches and tags, allow only tags matching `v*`. Add four
   environment secrets:

   | Secret | Value |
   | --- | --- |
   | `WEARMESH_KEYSTORE_BASE64` | The keystore as one line of base64: `base64 -w0 ~/keys/wearmesh-release.jks` (on macOS, `base64 -i ~/keys/wearmesh-release.jks`) |
   | `WEARMESH_KEYSTORE_PASSWORD` | Its password |
   | `WEARMESH_KEY_ALIAS` | `wearmesh` |
   | `WEARMESH_KEY_PASSWORD` | The same password |

   And one environment **variable** (not a secret: it is public), `WEARMESH_CERT_SHA256`, the
   key's SHA-256 fingerprint as `keytool -list -v -keystore ~/keys/wearmesh-release.jks` shows
   it (colons and capitals are fine). The workflow refuses an APK signed with any other key,
   since it could not update testers' installs:

   ```bash
   gh variable set WEARMESH_CERT_SHA256 --env release --repo Colorado-Mesh/wearmesh --body "<the SHA256 fingerprint>"
   ```

   Only the Release workflow uses the environment, and only for a `v*` tag, so pull requests and
   forks never see the key. Then, in Settings › Rules, add a tag ruleset for `v*` that only you
   can create, update or delete.

## A release

1. **Ready it in a pull request**: set `versionName` to the release (`0.1.0`), raise
   `versionCode` by one, and in `CHANGELOG.md` give the release's section its date, as
   `## 0.1.0 (12 November 2026)`. Merge it once CI passes.
2. **Tag it** on `main`:

   ```bash
   git checkout main && git pull
   git tag -a v0.1.0 -m "WearMesh 0.1.0"
   git push origin v0.1.0
   ```

3. **The Release workflow** (`.github/workflows/release.yml`) then:
   - stops if the tagged commit is not on `main`, if the tag is not `v` + `versionName`, if the
     version ends in `-dev`, or if `CHANGELOG.md` has no section for it or the section is still
     "not yet released";
   - runs the tests and builds the APK and the AAB, signed with the release key;
   - checks the APK is signed with that key alone, by its fingerprint;
   - keeps the AAB, for Google Play, as a workflow artifact for 90 days;
   - only then publishes the GitHub Release `WearMesh 0.1.0`, with the changelog section as its notes, the
     APK as `wearmesh-0.1.0.apk` and its SHA-256.
4. **Open the next version** in a pull request: `versionName` to `0.2.0-dev`, `versionCode` up by
   one, and a `## 0.2.0 (not yet released)` section at the top of `CHANGELOG.md`.

If a run fails, fix it, delete the tag (`git push --delete origin v0.1.0`, then
`git tag -d v0.1.0`), and tag again. Once a release is published and installed by anyone, never
reuse its version.

## A signed build at home

The release build signs itself when these are set; otherwise it is unsigned, as in CI:

```bash
export WEARMESH_KEYSTORE=~/keys/wearmesh-release.jks
export WEARMESH_KEY_ALIAS=wearmesh
read -rs -p "Key password: " WEARMESH_KEYSTORE_PASSWORD; echo
export WEARMESH_KEYSTORE_PASSWORD WEARMESH_KEY_PASSWORD=$WEARMESH_KEYSTORE_PASSWORD
./gradlew :wear:assembleRelease      # wear/build/outputs/apk/release/wear-release.apk
```

**Switching a watch between debug and release builds wipes WearMesh's data.** They are signed
with different keys, so Android will not install one over the other: the app has to be
uninstalled first, which deletes its messages, settings and the Alarms & reminders permission,
as the schema 6 reinstall did. Keep your own watch on debug builds while developing, and use a
release build only to check a release before testers get it.

## Google Play

Play App Signing will be given this same key as the app signing key ("use an existing app
signing key", exported with Google's PEPK tool), so testers can move from a GitHub APK to Play's
build without a reinstall. That is step 4 of the plan, with the developer account.

## API 35

`targetSdk` is 35 from version 24 (0.1.0-dev). Google Play has needed it for new Wear OS apps and
updates since 31 August 2026. The Pixel Watch 5 runs a newer Android, so Android 15's changes for
apps targeting 35 apply on it. Checked against the code:

| Android 15 change | WearMesh |
| --- | --- |
| A `BOOT_COMPLETED` receiver may not start some kinds of foreground service | The restart receiver only sets the Check-in alarm; both services are `connectedDevice`, which is not one of them |
| Edge-to-edge drawing enforced | Wear OS has no status or navigation bars to draw under; the screens are already full-screen and round |
| Apps that create a `PendingIntent` no longer lend it their right to start an activity from the background | The taps that open the app (notifications, the complication, the tiles) come from the system or the watch face, after the user's tap, which is still allowed. To be confirmed on the watch below |
| Foreground service time limits | Only for `dataSync` and `mediaProcessing`; not used |
| Exact alarms | Unchanged since Android 14: Check-in still asks for Alarms & reminders |

The database tests still run on Robolectric at API 34; nothing they test changed in 35.

### Watch checks for API 35

On the watch, with the version 24 debug build:

1. **Open from everything.** Tap a message notification, the complication, a message on the
   Messages tile, Reply and Check now on the tiles: each opens WearMesh where it should.
2. **Check-in.** Check-in at 15 minutes runs on time with the screen off (the log's
   `Check-in (alarm)` lines), and after a restart of the watch the Status tile shows a new next
   time within a minute or so.
3. **Live.** Live connects, a message arrives at once, and turning the radio off and on again
   reconnects.
4. **Send.** A channel message and a direct message go, from the app and from a notification's
   Quick reply.

All four passed on the Pixel Watch 5 on 2 November 2026 (version 24, during PR 32). A release
build signed at home with the release key showed the same SHA-256 fingerprint as the key.
