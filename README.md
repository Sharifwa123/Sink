# Sink

[![Android build](https://github.com/Sharifwa123/Sink/actions/workflows/android-build.yml/badge.svg)](https://github.com/Sharifwa123/Sink/actions/workflows/android-build.yml)

**By SHARIF TECHNOLOGIES**

> Offline communication when connectivity is unavailable.

Sink is an Android-first, offline-first mesh messenger. Two or more people
can exchange end-to-end encrypted text messages using nearby Android
devices as relays, without any dependency on a cloud server, an account,
or a phone number. When no mesh or internet route exists, Sink can fall
back to plain SMS as an explicit, user-confirmed last resort.

This is not a demo that talks about mesh networking — the routing engine
in `engine/mesh-engine` really does peer discovery bookkeeping, packet
signing/encryption, TTL/hop-bounded flooding, duplicate suppression, ACK
propagation, and store-and-forward retry, and it is proven by 40+
automated tests that simulate real multi-hop topologies (including nodes
disappearing and reappearing) — see `docs/TESTING.md`.

## Repository layout

```
engine/                     pure-Kotlin/JVM Gradle build — no Android dependency
  protocol/                 wire packet format, message ids, states, codecs
  crypto-core/               ECDH/ECDSA/AES-GCM/HKDF over the JVM's own JCA
  mesh-engine/               RoutingEngine, TransportManager, mesh simulator + tests

app/                         Android application module (Compose entry point, DI wiring)
core/
  common/                    dispatchers, application-scoped CoroutineScope
  crypto/                    Android Keystore-backed device identity
  database/                  Room: conversations, messages, contacts, peers
  datastore/                 Preferences DataStore: settings
  networking/                Nearby Connections / SMS / Internet transports, foreground service
  logging/                   structured, plaintext-free logger
  permissions/                permission bundles + rationale copy
feature/
  onboarding/ home/ conversations/ chat/ contacts/
  discovery/ mesh/ settings/ education/
docs/                        architecture, protocol, security, testing, release docs
```

## Why two Gradle builds

`engine/` has zero Android dependencies and needs only Maven Central, so
it builds and runs its full test suite anywhere a JDK is available —
including sandboxes with no Android SDK and no access to Google's Maven
repository (the situation this project was actually built in; see
`docs/ARCHITECTURE_AUDIT.md`). The root Android project depends on it as
an included build (`includeBuild("engine")` in `settings.gradle.kts`) and
adds the platform layer: Compose UI, Room, Keystore, Nearby Connections,
SMS, WorkManager.

## Building

```bash
# The pure-Kotlin core: builds and tests anywhere with a JDK 17+ and
# network access to Maven Central. No Android SDK required.
cd engine && ./gradlew test

# The full Android app: requires the Android SDK and network access to
# Google's Maven repository (dl.google.com) for AGP/Compose/Room/Hilt/
# Nearby Connections. From the repo root, with ANDROID_HOME set:
./gradlew :app:assembleDebug
```

This sandbox has neither, so the Android module tree's actual compile
verification happens in CI (`.github/workflows/android-build.yml`) —
check that workflow's status on the current commit rather than assuming
this repository has been fully compiled locally. See
`docs/ANDROID_LIMITATIONS.md` and `docs/IMPLEMENTATION_STATUS.md` for the
full, honest picture.

## Documentation

- `docs/ARCHITECTURE_AUDIT.md` — starting state and why the project is shaped this way
- `docs/ARCHITECTURE.md` — module map and data flow
- `docs/NETWORK_PROTOCOL.md` — the wire packet format
- `docs/ROUTING.md` — the routing/forwarding algorithm and why it was chosen
- `docs/SECURITY.md` — cryptography, key management, what's protected
- `docs/THREAT_MODEL.md` — what Sink defends against and what it doesn't
- `docs/ANDROID_LIMITATIONS.md` — real platform constraints this app works within
- `docs/PERMISSIONS.md` — every permission and why it's requested, and when
- `docs/TESTING.md` — the automated test suite
- `docs/DEVICE_TESTING.md` — manual multi-phone test procedure
- `docs/RELEASE.md` — release build/signing process
- `docs/IMPLEMENTATION_STATUS.md` — honest completed/partial/not-implemented breakdown
