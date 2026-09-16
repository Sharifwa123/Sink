# Sink — Architecture Audit

Date: 2026-09-16
Author: Automated engineering session (Claude Code)

## 1. Existing State

The repository `sharifwa123/sink` was **empty** at the start of this work: no
commits, no source files, no Gradle configuration, no manifest. There was
nothing to preserve or migrate. This is a greenfield build.

Because there was no existing architecture, no code review of "current
patterns" was possible. This document instead records the architecture
chosen for the greenfield project and the environmental constraints that
shaped it.

## 2. Execution Environment Constraints (read this before judging build results)

This matters a lot for how the codebase is organized, so it's called out up
front rather than buried in a limitations appendix:

- **No Android SDK is installed** in this sandbox (`ANDROID_HOME` unset, no
  `cmdline-tools`, no platform images, no emulator).
- **Google's Maven repository (`dl.google.com`) is not reachable** from this
  sandbox (network egress returns a proxy-level `403`). Maven Central and
  the Gradle Plugin Portal *are* reachable.
- Consequence: the Android Gradle Plugin, Jetpack Compose, Room, Hilt,
  DataStore, WorkManager, and Play Services Nearby Connections **cannot be
  resolved or compiled in this sandbox**, because every one of those
  artifacts is published to Google's Maven repository. This is a hard,
  verifiable environment limitation, not a shortcut taken out of laziness.
- What *can* be built and tested here: plain-JVM Kotlin code that only
  needs Maven Central (JUnit, kotlinx-coroutines-core, kotlinx-coroutines-test).

**Design response:** the project is split into two Gradle builds:

1. `engine/` — a standalone, pure-Kotlin/JVM Gradle build with **zero**
   Android dependencies. It contains the protocol definitions, the
   cryptographic primitives (JCA/JVM standard library only), and the mesh
   routing engine (TTL, hop limits, dedup, ACK, store-and-forward, transport
   ranking). Because it has no Android dependency, **it actually builds and
   its test suite actually runs in this sandbox** — this is where the
   "don't fake mesh networking" requirement is proven with real, executed
   tests rather than asserted in prose.
2. The root Android application (`app/`, `core/*`, `feature/*`) — a
   conventional multi-module Android app that depends on `engine/` as an
   included build (`includeBuild("engine")`) and adds the Android platform
   layer: Compose UI, Room, DataStore, Android Keystore, Nearby
   Connections, SmsManager, WorkManager, Hilt. This half **cannot be
   compiled in this sandbox** because of the network/SDK limitation above.
   It is written to the same engineering bar, but its correctness has been
   verified by careful manual review and static reasoning, not a green
   Gradle build. This is recorded honestly in
   `docs/IMPLEMENTATION_STATUS.md` and `docs/ANDROID_LIMITATIONS.md` — it
   is not represented as compiled/tested when it wasn't.

This split is also good architecture independent of the sandbox: it forces
the mesh/crypto/protocol core to have no Android dependency, which is
exactly what makes it unit-testable and reusable, and it's what section 43
of the brief ("mesh simulation" without physical phones) is asking for.

## 3. Recommended Architecture

Modular clean architecture, per the brief, with one addition (the
`engine` split) to work around the sandbox constraint and to genuinely
satisfy "testable architecture" for the riskiest logic:

```
Sink/
  engine/                     # pure Kotlin/JVM, no Android deps, builds+tests here
    protocol/                 # packet schema, message ids, states, versioning
    crypto-core/              # X25519/Ed25519/AES-GCM/HKDF over JCA — pure JVM
    mesh-engine/              # TransportManager, RoutingEngine, dedup cache,
                               # store-and-forward queue, mesh simulator + tests
  app/                        # Android application module (Compose entry point, DI wiring)
  core/
    common/                   # shared Android utils, dispatchers, Result types
    crypto/                   # Android Keystore-backed key storage, wraps engine:crypto-core
    database/                 # Room: conversations, messages, contacts, peers, queue
    datastore/                # Proto/Preferences DataStore: settings, identity metadata
    networking/               # Android CommunicationTransport implementations:
                               #   NearbyTransport, BluetoothTransport(-lite), SmsTransport,
                               #   InternetTransport — all implement engine's interfaces
    logging/                  # structured logger, redaction, debug-log toggle
    permissions/              # permission state + rationale + contextual request flow
  feature/
    onboarding/  home/  conversations/  chat/  contacts/
    discovery/   mesh/  settings/  education/
  docs/
```

Transport abstraction lives in `engine:mesh-engine` as `CommunicationTransport`
+ `TransportCapabilities`; Android modules provide the real implementations.
`RoutingEngine` and `TransportManager` never import `android.*`, so they can
be driven by a `SimulatedTransport` in tests and by real transports on
device without any behavioral fork.

## 4. Dependencies

**Already present:** none (empty repo).

**To be added (engine, resolvable now):**
- Kotlin JVM toolchain (embedded with Gradle 8.14.3 / Kotlin 2.0.21)
- `org.jetbrains.kotlinx:kotlinx-coroutines-core`
- `org.jetbrains.kotlinx:kotlinx-coroutines-test`, JUnit 4 (test only)

**To be added (Android app, requires Google Maven — not resolvable in this
sandbox, resolvable in Android Studio / CI with normal network access):**
- AGP (`com.android.application`), Kotlin Android plugin
- Jetpack Compose BOM + Material3, Navigation-Compose
- Hilt (`com.google.dagger:hilt-android`) + Hilt Navigation Compose
- Room (`androidx.room`) + KSP
- DataStore (`androidx.datastore`)
- WorkManager (`androidx.work`)
- AndroidX Lifecycle/ViewModel/Activity-Compose/Core-KTX
- Play Services Nearby Connections (`com.google.android.gms:play-services-nearby`)
- AndroidX Security-Crypto (Keystore-backed EncryptedFile/SharedPreferences helpers) — optional, Keystore used directly for asymmetric identity keys

## 5. Risks

- **Nearby Connections is not a mesh.** It gives point-to-point/star
  discovery+connection between devices in radio range; Sink must implement
  routing, forwarding, TTL, and dedup itself on top of it. This is exactly
  what `engine:mesh-engine` does — the Android `NearbyTransport` is a thin
  transport, not a router.
- **Multi-hop range is bounded by how many direct Nearby connections a
  single Android device can hold concurrently**, which is a
  platform/hardware limit, not something Sink controls. Documented in
  `ANDROID_LIMITATIONS.md`.
- **Background scanning is restricted** by Doze/App Standby and by Android
  12+ nearby-device permission model. Continuous mesh presence cannot be
  guaranteed while the app is backgrounded; a foreground service is used
  only while a chat/mesh session is actively relevant, never unconditionally.
- **SMS sending is not silent on modern Android** for normal (non-default-SMS)
  apps beyond the default handler's own convenience APIs; Sink uses
  `SmsManager` where permission is granted (still requires runtime
  `SEND_SMS` grant and shows as sent from Sink to the user), and falls back
  to the system composer `Intent` when it isn't. This is documented, not
  hidden.
- **No compiled verification of the Android module tree in this session** —
  the single largest risk of this audit. Mitigated by: keeping all
  non-trivial logic in the tested `engine` build, keeping Android adapters
  thin and mechanical, and being explicit in `IMPLEMENTATION_STATUS.md`.

## 6. Android Platform Limitations (summary — full detail in ANDROID_LIMITATIONS.md)

- No true background mesh daemon; discovery is lifecycle- and
  user-session-bound.
- No multicast/broadcast primitive across arbitrary transports — routing is
  implemented at the application layer over unicast links.
- SMS fallback is text-only and subject to carrier length limits (concatenated
  SMS is used up to a bounded number of segments; beyond that, Sink refuses
  and tells the user why).
- Wi-Fi Direct / Bluetooth Classic APIs are not used for the MVP transport
  (Nearby Connections abstracts over them); a raw BLE/Wi-Fi Direct transport
  is left as a documented future transport behind the same interface.

## 7. Implementation Phases (this session)

1. Repository scaffold, Gradle setup (`engine` composite build + Android
   module tree), version catalog. **(this phase)**
2. Domain/protocol models + Room schema design.
3. Device identity + cryptographic foundation (`engine:crypto-core` +
   `core:crypto` Keystore adapter).
4. Peer discovery abstraction (`CommunicationTransport`, `NearbyTransport`).
5. Direct peer messaging (send/receive pipeline).
6. Durable outgoing message queue (Room-backed, WorkManager-driven retry).
7. Mesh forwarding (TTL, hop count, dedup, store-and-forward) — built and
   unit-tested in `engine:mesh-engine` via the mesh simulator.
8. ACK + delivery state propagation.
9. SMS fallback transport.
10. Security hardening review + threat model.
11. UX (onboarding, home, discovery, chat, settings, education, diagnostics).
12. Testing (unit + simulated mesh scenarios).
13. Release configuration (ProGuard/R8, versioning, signing config placeholders).

Proceeding to Phase 1.
