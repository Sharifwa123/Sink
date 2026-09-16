# Testing

## Automated tests: `engine/` (43 tests, all passing, run in this project's CI-equivalent — see below)

Run with:

```bash
cd engine && ./gradlew test
```

This is the only part of the codebase actually compiled and tested as
part of building this project (see `docs/ANDROID_LIMITATIONS.md` for
why) — and it's also where all of the mesh/routing/crypto logic that the
product brief calls "not fake mesh networking" lives.

| Test class | Count | Covers |
|---|---|---|
| `SinkPacketTest` | 6 | TTL expiry, hop-limit logic, conversation id symmetry |
| `PacketCodecTest` | 4 | Wire encode/decode round trip, unsupported version rejection |
| `DeviceIdentityCodecTest` | 2 | HELLO payload encode/decode round trip |
| `EciesCipherTest` | 6 | Encrypt/decrypt round trip, wrong-key failure, tamper detection, fresh nonce per message, signature verify/reject, fingerprint stability |
| `MessageIdCacheTest` | 4 | New vs. duplicate detection, time-based eviction, bounded-size eviction |
| `PeerRateLimiterTest` | 4 | Per-window limit, reset after window, independent per-peer tracking |
| `RetryPolicyTest` | 2 | Exponential backoff growth and cap |
| `TransportManagerTest` | 4 | Transport priority ranking, SMS excluded from auto-broadcast, unreachable-peer failure, connect/disconnect listener firing |
| `RoutingEngineSmsFallbackTest` | 3 | Explicit SMS resend after automatic retries fail, failure propagation, unknown-message-id rejection |
| `MeshRoutingSimulationTest` | 8 | See below |

### `MeshRoutingSimulationTest` — the mesh simulator

This is the "mesh simulation layer" called for by the product brief
(§43): a `SimulatedMeshNetwork` + `SimulatedTransport` stand in for real
radios with a mutable adjacency graph, so the *actual* `RoutingEngine`
and `TransportManager` — the same classes driven by real transports on a
phone — are exercised across topologies that would otherwise need
several physical devices:

1. **Direct delivery** between two connected nodes, ACK round-trip to `DELIVERED`.
2. **Four-hop chain (A→B→C→D)**: end-to-end delivery, relays (B, C) never
   record the message as their own, hop count matches the number of
   relays.
3. **Duplicate suppression across redundant paths**: two independent
   two-hop routes to the same destination deliver exactly once.
4. **TTL expiration**: a message with no route and a short TTL is marked
   `EXPIRED`, not left `QUEUED` forever.
5. **Hop budget exceeded**: a `maxHops` too small for the actual path
   length is dropped by an intermediate relay and, after retries exhaust,
   marked `FAILED` at the sender.
6. **Store-and-forward**: exactly the product brief's example — B relays
   for A→C, then B disappears (`network.disconnectAll`), a new message
   queues instead of failing silently, then B reconnects and the queued
   message delivers.
7. **Flooding resistance**: a neighbor sending far more packets than the
   rate limit allows only gets that many delivered, not all of them.
8. **HELLO handshake**: two newly-connected nodes each emit the other's
   verified `DeviceIdentity` on `discoveredIdentities`.

A subtlety worth recording here in case it resurfaces: this simulator
runs on `kotlinx-coroutines-test`'s `backgroundScope`, and a real bug was
found and fixed during development where `network.connect()` could
trigger an automatic HELLO send *before* the receiving side's flow
collector had subscribed — a `MutableSharedFlow` with `replay = 0` does
not retain values for a subscriber that hasn't subscribed yet, even with
spare buffer capacity. The fix is a `runCurrent()` immediately after
starting all nodes, before any topology change, in every test — letting
every collector "warm up" and subscribe first.

## What's not automatically tested

Anything requiring the actual Android SDK, an emulator, or Instrumented
tests (Compose UI tests, Room migration tests against a real SQLite
engine, Nearby Connections against real radios, Keystore-backed key
generation) has not been executed in this project's history — see
`docs/ANDROID_LIMITATIONS.md`. `docs/DEVICE_TESTING.md` describes the
manual procedure for verifying the parts that need real hardware.
