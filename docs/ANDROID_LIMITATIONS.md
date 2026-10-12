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

## Background behavior: runs while open, stops when you remove it from Recents

`MeshForegroundService` starts when Sink is opened and keeps mesh
discovery/connections alive — with a visible, low-priority ongoing
notification, as Android requires for any foreground service — for as
long as Sink stays in your device's Recents/app-switcher list, including
while you're using another app or the screen is off. It stops when you
explicitly swipe Sink away from Recents (`Service.onTaskRemoved`), or when
you turn the "Nearby discovery" setting off. Merely backgrounding the app
(pressing home, switching apps without removing Sink from Recents) does
**not** stop it — that used to be the behavior (stopping on `Activity.
ON_STOP`), which made the mesh look dead the instant you tapped away; it
no longer does.

This is still not an unconditional always-on daemon: it is a real Android
foreground service, bound by the same OS rules as a music player or an
ongoing call. It cannot survive the user force-stopping the app from
Settings, a device reboot without reopening Sink, or Android's rare
aggressive background kills on some OEM skins. There is no way for an app
to run invisibly forever in the background on stock Android, and Sink
does not attempt to pretend otherwise.

**Consequence**: a message can relay through any device that has opened
Sink at least once since its last reboot/force-stop and hasn't swiped it
out of Recents — not just one with the app currently in the foreground.

## Sink cannot use another app's Bluetooth/Wi-Fi connection, or message someone without Sink installed over the mesh

Sink cannot see, join, or relay through a Bluetooth/Wi-Fi/hotspot
connection or file-transfer session that belongs to a different app (for
example Xender or Shareit/Xshare). Android's app sandboxing gives apps no
API to observe, attach to, or borrow another app's active
radio session — this isn't a missing Sink feature, it's a boundary the
platform enforces for every app, by design. The two devices involved
would also both need to speak Sink's own wire protocol to exchange a
Sink message at all — a raw Bluetooth or Wi-Fi link by itself carries no
"this is a Sink message" meaning without Sink's own encoding and
encryption on both ends.

The real, working equivalent of "message someone even if they don't have
Sink yet" is the **invite-by-phone-number** flow (Contacts → **+**): Sink
sends them a plain, ordinary SMS saying who's inviting them and where to
get the app — the same shape as WhatsApp's "invite a friend" link — and
once they install Sink and the two of you are in mesh/SMS range of each
other, a real conversation starts. This is a one-way invitation, not a
message exchange: Sink has no way to detect in advance whether an
arbitrary phone number already has the app, and cannot deliver a Sink
message to a phone that has never run it.

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

## Continuous integration is the compile/test gate

`engine/` (pure Kotlin, needs only Maven Central) builds and runs its
test suite in any environment with a JDK. The full Android app tree
(`app/`, `core/*`, `feature/*`) additionally needs the Android SDK and
network access to Google's Maven repository for the Android Gradle
Plugin, Jetpack Compose, Room, Hilt, DataStore, WorkManager, and Play
Services Nearby Connections — `.github/workflows/android-build.yml`
builds and tests both on every push and is the authoritative answer to
"does this compile". See `docs/IMPLEMENTATION_STATUS.md` for current
feature completeness.
