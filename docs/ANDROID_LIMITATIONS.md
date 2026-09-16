# Android Platform Limitations

Real constraints Sink is built around, not glossed over.

## Nearby Connections is not a mesh

Google Play Services Nearby Connections gives device discovery and
direct (one-hop) links over whichever radio it picks (BLE, Bluetooth
Classic, or Wi-Fi) — nothing more. It does not provide multi-hop routing,
store-and-forward, or deduplication. Every bit of "mesh" behavior in Sink
— TTL/hop-bounded flooding, dedup, ACK propagation, retry — is
implemented in `engine:mesh-engine` on top of whatever transport is
available, and is exercised the same way whether the transport is real
(`NearbyTransport`) or simulated (`SimulatedTransport`) in tests.

## How many peers can one device relay for at once?

`P2P_CLUSTER` strategy (used by `NearbyTransport`) supports multiple
simultaneous connections, but the actual number a given device can
sustain is a hardware/OS limit Sink doesn't control — it varies by
chipset, Android version, and current radio load. Sink doesn't hardcode
an assumed limit; `TransportManager.reachablePeers()` simply reflects
whatever the transport currently reports.

## No continuous background mesh daemon

Sink does not scan for or maintain mesh connections indefinitely in the
background. `MeshForegroundService` runs — with a visible, low-priority
notification, as Android requires for a foreground service — only while
the app is actually in the foreground (`MainActivity`'s lifecycle
observer starts it on `ON_START`, stops it on `ON_STOP`). This is a
deliberate choice, not an accepted-but-unwanted constraint: continuously
scanning in the background would be exactly the "unnecessary background
activity" the product brief warns against, and Android's Doze/App
Standby would fight it anyway on most devices.

**Consequence**: a message can only relay through a device whose Sink app
is currently open (or, if the OS keeps the foreground service warm
briefly, very recently backgrounded). This is disclosed in the
onboarding/education screens' framing ("keep Sink open and nearby devices
will appear") rather than implied to be always-on.

## Runtime permission model varies significantly by API level

`SinkPermission.manifestPermissions()` (`core:permissions`) branches on
`Build.VERSION.SDK_INT`:

- **API 26–30**: classic Bluetooth discovery requires
  `ACCESS_COARSE_LOCATION` (an OS requirement, not Sink's choice — nearby
  BLE scan results have historically been treated as location-adjacent
  data pre-Android 12).
- **API 31–32 (Android 12/12L)**: `BLUETOOTH_SCAN`/`BLUETOOTH_ADVERTISE`/
  `BLUETOOTH_CONNECT`, with `BLUETOOTH_SCAN` declared
  `usesPermissionFlags="neverForLocation"` — no location permission
  needed.
- **API 33+ (Android 13+)**: additionally `NEARBY_WIFI_DEVICES` (also
  `neverForLocation`) for the Wi-Fi-based portion of Nearby Connections;
  `POST_NOTIFICATIONS` also becomes a runtime permission for the first
  time.

Sink requests these contextually (during onboarding's "Find nearby Sink
users" step, or when Discovery is first opened) — never all at once at
install, and never without the explanatory copy in
`SinkPermission.rationale()` shown first.

## SMS sending is not silent for a normal app

Beyond a device's default SMS handler, an ordinary app like Sink still
needs the user to have granted `SEND_SMS` at runtime, and the OS will
generally surface that an SMS was sent (this is intentional anti-abuse
design in Android, not something to route around). Sink does not attempt
to disguise or suppress this — `SinkTransport`'s permission rationale and
the onboarding SMS explainer both tell the user plainly that choosing
"Send by SMS" sends a real SMS from their own number.

`SmsManager.getDefault()` is used rather than the newer per-subscription
API (`Context.getSystemService(SmsManager::class.java)`, API 31+), so
Sink does not currently let a dual-SIM user choose which SIM sends the
fallback SMS — a documented, minor gap rather than a silent one.

## No verified compile of the Android module tree in this project's history

This entire Android app tree (`app/`, `core/*` except `engine/`,
`feature/*`) was written in a sandbox with **no Android SDK installed and
no network access to Google's Maven repository** (`dl.google.com`
returns a proxy-level 403 there) — meaning the Android Gradle Plugin,
Jetpack Compose, Room, Hilt, DataStore, WorkManager, and Play Services
Nearby Connections could not be resolved, let alone compiled, in that
environment. Only `engine/` (pure Kotlin, needs only Maven Central) was
actually built and tested there. See `docs/ARCHITECTURE_AUDIT.md` for the
full story and `docs/IMPLEMENTATION_STATUS.md` for what that means for
confidence in this codebase — it is written to the same bar as the tested
engine and has had careful manual review, but "compiles cleanly with
Android Studio and a real SDK" is the first thing to verify in a normal
development environment, not something this project can claim already
happened.
