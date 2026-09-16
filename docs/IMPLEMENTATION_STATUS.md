# Implementation Status

Written at the end of this build session. Read this before trusting any
other claim in this repository's documentation — this is the honest,
current summary.

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

## PARTIALLY COMPLETED

- **Internet transport**: interface implemented, but `send()` always
  returns `Failed` — no backend/signaling service exists to make internet
  messaging actually work, by design (the brief explicitly says the core
  mesh must not depend on a backend). Wiring a real one is future work.
- **Forward secrecy**: per-message ephemeral-sender ECDH gives partial
  forward secrecy, not a full ratcheting session protocol. See
  `docs/SECURITY.md`.
- **Notifications**: `POST_NOTIFICATIONS` is declared and the mesh
  foreground service notification works, but there is no dedicated
  runtime-permission priming screen or per-message notification
  (incoming-message push notification) implemented yet.
- **Blocked-user enforcement**: enforced at the routing layer for
  incoming `DATA` packets (fixed during this session — see
  `docs/THREAT_MODEL.md` §8); does not yet also suppress a blocked
  contact's `HELLO`/`ACK` traffic from being relayed onward by this
  device (it will still forward them for someone else, just won't accept
  DATA packets addressed to itself from a blocked sender).
- **Diagnostics screen**: shows real operational data (device id,
  protocol version, network status, connected-peer count, queue depth,
  pending-ACK count) but not the fuller list the product brief describes
  (last successful transmission timestamp, last failure, database status)
  — a reasonable follow-up, not implemented here.

## NOT IMPLEMENTED

- QR-code-based contact verification (safety-number text comparison is
  implemented; QR scanning would need a camera + barcode library, judged
  out of scope for this pass — see `docs/SECURITY.md`).
- Media (image/audio/video/file) transport — text-only, as the brief
  prioritizes.
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
  none could run in this project's build sandbox (no Android SDK). See
  below.

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

`cd engine && ./gradlew test` — **43 tests, 0 failures**, as of this
writing. Full breakdown in `docs/TESTING.md`. This is a real, executed
result from this project's own build history, not an aspirational claim.

## BUILD RESULT

- **`engine/` (pure Kotlin/JVM)**: builds and tests successfully with
  Gradle 8.14.3 / Kotlin 2.0.21 / JDK 21, verified repeatedly during
  development.
- **Root Android project (`app/`, `core/*`, `feature/*`)**: **has not been
  compiled** in this project's history. The sandbox this was built in has
  no Android SDK and cannot reach `dl.google.com` (Google's Maven
  repository), so the Android Gradle Plugin, Compose, Room, Hilt,
  DataStore, WorkManager, and Play Services Nearby Connections could not
  be resolved. Every file was written and manually reviewed to the same
  engineering bar as the tested `engine/` code, cross-checked for
  matching function signatures across module boundaries (navigation
  callbacks, DI providers, DAO/entity fields), but "green build in
  Android Studio with a real SDK" is the outstanding verification step —
  see `docs/ANDROID_LIMITATIONS.md` and `docs/RELEASE.md`.

## NEXT RECOMMENDED STEPS

1. Open the project in Android Studio (or any environment with network
   access to Google's Maven) and run `./gradlew :app:assembleDebug` —
   fix whatever surfaces; multi-module Kotlin/Compose/Hilt projects
   typically have a handful of small wiring issues on a first real
   compile (missing dependency declarations, import path typos) even when
   carefully hand-written.
2. Run the app on two to three physical devices and work through
   `docs/DEVICE_TESTING.md`.
3. Add a dedicated `POST_NOTIFICATIONS` runtime-permission priming step
   and incoming-message push notifications.
4. Decide on and implement a proper session ratchet (e.g., a
   Signal-Protocol-style Double Ratchet) if stronger forward secrecy is a
   priority before wider release.
5. Add Compose UI tests and Room migration tests once the module tree is
   confirmed to compile.
6. Revisit whether Curve25519 is now safely usable given Sink's actual
   minSdk before the first public release, per the note in
   `docs/SECURITY.md`.
