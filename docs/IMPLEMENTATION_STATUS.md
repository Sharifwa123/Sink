# Implementation Status

Read this before trusting any other claim in this repository's
documentation — this is the honest, current summary of what's done,
partially done, and not implemented.

## COMPLETED

- Full mesh routing engine (`engine/mesh-engine`): TTL/hop-bounded
  controlled flooding, per-neighbor duplicate suppression, ACK
  propagation, store-and-forward with exponential-backoff retry
  (immediate on peer reconnect + periodic safety-net sweep), per-neighbor
  rate limiting, oversized-packet rejection, explicit SMS-fallback resend
  path independent of automatic retry lifecycle.
- Self-certifying HELLO identity handshake, automatic on peer connect,
  feeding a real contacts table (`PeerIdentityPersister`) — Discovery and
  Contacts show real discovered peers, never fabricated ones.
- End-to-end message encryption (ephemeral ECDH + HKDF-SHA256 +
  AES-256-GCM) and ECDSA packet signing/verification, all pure JCA, unit
  tested on the JVM.
- Device identity lifecycle: Keystore-hardware-backed (StrongBox where
  available) non-exportable signing key, generated once and never
  silently regenerated; software agreement key pair wrapped at rest by a
  separate hardware-backed AES key.
- Wire protocol with versioning, a dependency-free binary codec, and a
  distinct HELLO payload codec — both round-trip tested.
- Room schema for messages/conversations/contacts/peers with real DAOs;
  `RoomMessageQueue`/`RoomIdentityDirectory` implement the engine's
  storage interfaces, including blocked-contact key withholding enforced
  at the routing layer (not just hidden in the UI).
- Nearby Connections transport (`NearbyTransport`, P2P_CLUSTER,
  endpoint↔DeviceId mapping, payload send/receive wired to the wire
  codec).
- SMS fallback transport: outbound via `SmsManager`, inbound via a
  manifest-registered `SmsReceiver` parsing a `SINKv1:`-prefixed payload,
  segment-count limit enforced with a clear user-facing refusal above it.
- WorkManager periodic retry sweep (Hilt-assisted worker) and a
  foreground service that starts/stops with app visibility, gated on the
  "Nearby discovery" setting.
- Full onboarding flow (welcome → concept → display name → contextual
  nearby-permission request → SMS explainer), Home (status banner +
  conversation list), Discovery, Contacts (with safety-number display and
  verification marking), Chat (delivery-state-aware bubbles, SMS-fallback
  action gated on the setting), Message Details, mesh visualization
  (real, direct-connection-only — no fabricated topology), "How Sink
  Works" education content, Settings (all toggles functionally wired, not
  cosmetic), Diagnostics, About.
- 43 automated tests, all passing, covering the engine end-to-end
  including multi-hop simulation, TTL, dedup, ACK, store-and-forward,
  flooding resistance, and the HELLO handshake. See `docs/TESTING.md`.
- ProGuard/R8 rules, conditional release signing config (no secrets
  committed), versioned `applicationId`, debug/release build type
  separation, vector adaptive app icon.
- SECURITY.md/THREAT_MODEL.md bundled into the APK as assets and readable
  from About → Security / Threat model, instead of only existing on
  GitHub — these are user-facing documents, not developer-only ones.
- Optional in-app update check (`UpdateChecker`, `core:networking`):
  queries GitHub's public releases API on Home launch, shows an "Update
  available" banner with a direct download link when a newer release
  exists. Toggleable from Settings; the only network call Sink makes on
  its own (see `docs/SECURITY.md`). CI publishes permanent, public GitHub
  Releases (`latest-debug` rolling release, plus versioned signed
  releases once signing is configured) for this to point at.

## PARTIALLY COMPLETED

- **Internet transport**: interface implemented, but `send()` always
  returns `Failed` — no backend/signaling service exists to make internet
  messaging actually work. This is by design: the core mesh must not
  depend on a backend. Wiring a real one is future work.
- **Forward secrecy**: per-message ephemeral-sender ECDH gives partial
  forward secrecy, not a full ratcheting session protocol. See
  `docs/SECURITY.md`.
- **Notifications**: `POST_NOTIFICATIONS` is declared and the mesh
  foreground service notification works, but there is no dedicated
  runtime-permission priming screen or per-message notification
  (incoming-message push notification) implemented yet.
- **Blocked-user enforcement**: enforced at the routing layer for
  incoming `DATA` packets (see `docs/THREAT_MODEL.md` §8); does not yet also suppress a blocked
  contact's `HELLO`/`ACK` traffic from being relayed onward by this
  device (it will still forward them for someone else, just won't accept
  DATA packets addressed to itself from a blocked sender).
- **Diagnostics screen**: shows real operational data (device id,
  protocol version, network status, connected-peer count, queue depth,
  pending-ACK count) but not a fuller list that would also be useful
  (last successful transmission timestamp, last failure, database status)
  — a reasonable follow-up, not implemented here.

## NOT IMPLEMENTED

- QR-code-based contact verification (safety-number text comparison is
  implemented; QR scanning would need a camera + barcode library, judged
  out of scope for this pass — see `docs/SECURITY.md`).
- Media (image/audio/video/file) transport — text-only for this release.
- Key rotation / multi-device identity / device-replacement identity
  migration flow.
- A full mesh topology graph beyond one hop from each device's own
  vantage point (the mesh visualization screen is honestly scoped to
  what's actually known — see `docs/ARCHITECTURE.md`).
- Deep-linking to system Settings for a permanently-denied permission.
- Localization beyond English (all strings are in Android string
  resources / Kotlin string literals structured so this is addable later,
  but no second locale exists yet).
- Compose UI tests, Room migration tests, and any instrumented test —
  not yet written; CI currently runs the `engine/` unit suite and an
  APK assembly check only. See below.

## KNOWN ANDROID LIMITATIONS

See `docs/ANDROID_LIMITATIONS.md` in full. Summary: Nearby Connections
provides one-hop links only (routing is Sink's own code, not the
platform's); the number of simultaneous relay connections a device can
sustain is hardware-dependent and not controlled by Sink; there is no
continuous background mesh daemon (by design, to respect Android's
background-execution limits and avoid unnecessary battery use); the
runtime permission set legitimately varies by API level; SMS sending is
never silent, by OS design.

## KNOWN SECURITY LIMITATIONS

See `docs/SECURITY.md` and `docs/THREAT_MODEL.md` in full. Summary: P-256
instead of Curve25519 for minSdk-26 compatibility; the agreement private
key is envelope-encrypted rather than fully Keystore-custodied (an API
26–30 compatibility tradeoff); first-contact identity is trust-on-first-use;
no traffic-analysis resistance (no padding/cover traffic); no
in-app biometric/PIN re-lock; no Sybil resistance beyond normal contact
trust.

## TEST RESULTS

`cd engine && ./gradlew test` — **43 tests, 0 failures**. Full breakdown
in `docs/TESTING.md`.

## BUILD RESULT

- **`engine/` (pure Kotlin/JVM)**: builds and tests successfully with
  Gradle 8.14.3 / Kotlin 2.0.21 / JDK 21.
- **Root Android project (`app/`, `core/*`, `feature/*`)**: builds
  successfully — `./gradlew :app:assembleDebug` passes in CI on every
  push. See `docs/RELEASE.md` for release/signing configuration.

## CONTINUOUS INTEGRATION

`.github/workflows/android-build.yml` runs on every push/PR: the full
`engine/` test suite, then `./gradlew :app:assembleDebug`. Check that
workflow's status on the current commit for the current build health —
see the badge in `README.md`. On every push to the default branch it
also publishes a downloadable build to GitHub Releases; see
`docs/RELEASE.md` for what the workflow does and how to enable signed
release builds.

## NEXT RECOMMENDED STEPS

1. Run the app on two to three physical devices and work through
   `docs/DEVICE_TESTING.md`.
2. Add a dedicated `POST_NOTIFICATIONS` runtime-permission priming step
   and incoming-message push notifications.
3. Decide on and implement a proper ratcheting session protocol (e.g., a
   Signal-Protocol-style Double Ratchet) if stronger forward secrecy is a
   priority before wider release.
4. Add Compose UI tests and Room migration tests.
5. Revisit whether Curve25519 is now safely usable given Sink's actual
   minSdk before the first public release, per the note in
   `docs/SECURITY.md`.
