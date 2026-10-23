# protocol

The MeshCore companion protocol in pure Kotlin, with no Android dependencies: the frame codec (`Companion.kt`), the command queue that runs a session with a radio (`CompanionClient.kt`), and the telemetry reader it needs (`CayenneLpp.kt`). Unit tests run on the JVM: `./gradlew :protocol:test`.

## Where it comes from

These files are copied from [MeshCore TAK Bridge](https://github.com/Colorado-Mesh/meshcore-tak-bridge) at commit `33baf18` (21 October 2026), with its tests, under the same GPL-3.0 license. The package name is kept as `org.coloradomesh.takbridge.protocol` so the copy can be swapped for a shared module without touching the watch app.

One change from the bridge: `CompanionClient` takes an `appName` (default `"TAKBridge"`, as before), so the watch introduces itself to the radio as `WearMesh`. That change should go upstream when the module is shared.

## Plan

Milestone M1 in [docs/design.md](../docs/design.md) replaces this copy with one module that both apps build against. Until then, fixes made here or in the bridge need copying across by hand.
