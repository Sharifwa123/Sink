# Architecture

## Layering

```
feature/*  (Compose UI + Hilt ViewModels)
    │
    ├── core:database   (Room: source of truth for messages/conversations/contacts/peers)
    ├── core:datastore  (Preferences DataStore: settings)
    ├── core:crypto     (Android Keystore-backed identity)
    ├── core:networking (transports, foreground service, WorkManager retry)
    ├── core:permissions
    └── core:logging
            │
            ▼
    engine:mesh-engine  (RoutingEngine, TransportManager — zero Android deps)
    engine:crypto-core  (ECDH/ECDSA/AES-GCM/HKDF — zero Android deps)
    engine:protocol     (packet schema, ids, states, codecs — zero Android deps)
```

Nothing in `engine/` imports `android.*`. This is not a style preference —
it is what makes `RoutingEngine`'s TTL/hop/dedup/ACK/store-and-forward
logic testable via `engine:mesh-engine`'s simulator (`SimulatedMeshNetwork`,
`SimulatedTransport`, `SimulatedNode`) instead of asserted by hand. See
`docs/TESTING.md`.

## Core objects

- **`SinkPacket`** (`engine/protocol`): the wire unit routed through the
  mesh. Carries `senderId`/`destinationId`/`conversationId`, `ttlSeconds`/
  `hopCount`/`maxHops`, an encrypted `ciphertext` + `EncryptionMetadata`,
  and a `senderSignature`. Relays only ever read/mutate the routing
  fields — never the ciphertext.
- **`DeviceIdentity`**: a device's exchangeable identity (device id +
  signing/agreement public keys + display name). Distinct from `PeerInfo`
  (mesh-engine), which is the *runtime* state (last seen, connection
  state) for a peer whose `DeviceIdentity` is already known.
- **`RoutingEngine`** (`engine/mesh-engine`): owns the local identity, the
  dedup cache, the outgoing retry/backoff state, and the HELLO handshake
  that lets two devices learn each other's public keys on first contact
  (self-certifying, trust-on-first-use — see `docs/SECURITY.md`).
- **`TransportManager`**: ranks and fans a packet out across whichever
  `CommunicationTransport`s are currently connected to a peer. SMS is
  deliberately excluded from this automatic broadcast (see
  `docs/ROUTING.md`).
- **`CommunicationTransport`**: the interface real transports
  (`NearbyTransport`, `SmsTransport`, `InternetTransport`) and the test
  simulator's `SimulatedTransport` both implement. `RoutingEngine` cannot
  tell the difference.

## Data flow: sending a message

1. `ChatViewModel.sendMessage()` calls `RoutingEngine.sendMessage(peerId, body, conversationId)`.
2. `RoutingEngine` looks up the recipient's agreement public key via
   `IdentityDirectory` (backed by `RoomIdentityDirectory` → the contacts
   table). If unknown, the send is rejected with a message the UI shows
   directly — not a generic error.
3. The message is enqueued into `MessageQueue` (backed by `RoomMessageQueue`,
   which also upserts/updates the `ConversationEntity`) as `QUEUED`, then
   encrypted (ECIES: ephemeral ECDH + HKDF + AES-256-GCM) and signed.
4. `TransportManager.broadcast()` fans the packet to every currently
   reachable mesh-capable peer. The message's `deliveryState` is updated
   to `SENT_TO_PEER` (direct neighbor was the destination), `RELAYING`
   (handed to a relay), or stays `QUEUED` (no route yet).
5. `RoutingEngine.retrySweep()` — driven by `MeshRetryWorker` (WorkManager,
   every 15 minutes) and immediately whenever a new peer connects — retries
   with exponential backoff until ACKed, expired, or retries are exhausted.
6. If retries exhaust (`FAILED`), the packet is *not* discarded — the UI
   can still call `RoutingEngine.sendViaSpecificTransport(id, SMS)`
   explicitly, which is the only way SMS is ever used to send.

## Data flow: receiving a message

1. A transport's `incomingPackets` flow emits an `IncomingPacket` to
   `TransportManager`, which attaches which transport it arrived on and
   notifies `RoutingEngine`.
2. `RoutingEngine.handleReceivedPacket`: rate-limits by the immediate
   neighbor, rejects oversized packets, drops unknown protocol versions
   and expired packets, then checks the dedup cache — a duplicate is
   dropped immediately, never reprocessed or re-forwarded.
3. If the packet isn't addressed to this device, and its hop budget isn't
   exhausted, it's forwarded to every other reachable peer.
4. If it is addressed to this device: `ACK` packets clear the sender's
   retry state and mark `DELIVERED`; `DATA` packets are signature-verified
   against the directory, decrypted, persisted, emitted on
   `incomingMessages`, and acknowledged back toward the sender; `HELLO`
   packets are self-verified (see `docs/SECURITY.md`) and emitted on
   `discoveredIdentities`, which `PeerIdentityPersister` turns into a
   contacts-table row.

## Dependency injection

Every core/feature module that declares `@Inject`/`@Module` applies the
Hilt Gradle plugin + KSP directly (the standard multi-module Hilt
pattern) rather than centralizing everything in `:app`. `core:networking`'s
`NetworkingModule` is where the cross-cutting pieces get wired together —
it is the one place that depends on `core:crypto`, `core:database`, and
`engine:mesh-engine` simultaneously to construct the singleton
`RoutingEngine` and `TransportManager`.
