# Network Protocol

Protocol version: `1` (`PROTOCOL_VERSION` in `engine/protocol`). A packet
carrying a version this device doesn't recognize is dropped without being
processed or forwarded — never crashes, never partially processed.

## Packet (`SinkPacket`)

| Field | Type | Notes |
|---|---|---|
| `protocolVersion` | Int | checked before anything else |
| `packetType` | `DATA` \| `ACK` \| `HELLO` | |
| `messageId` | UUID string | globally unique; never a local auto-increment id |
| `senderId` | string | fingerprint of the sender's signing public key |
| `destinationId` | string | fingerprint of the intended final recipient |
| `conversationId` | string | `dm:<sorted device ids>` — order-independent |
| `createdAtEpochMillis` | Long | used for TTL expiry |
| `ttlSeconds` | Int | default 24h |
| `hopCount` | Int | incremented by each relay, **not** signed (see below) |
| `maxHops` | Int | default 6; a relay drops the packet once reached |
| `ciphertext` | bytes | AES-256-GCM output; empty for `ACK` |
| `encryptionMetadata` | `{algorithm, senderEphemeralPublicKey, nonce}` | `"NONE"` for `ACK`/`HELLO` self-cert payloads that don't need confidentiality |
| `senderSignature` | bytes | ECDSA over the canonical byte encoding, see below |
| `acknowledgedMessageId` | UUID string, optional | only set on `ACK` |

### Wire encoding

`PacketCodec.encode`/`decode` (`engine/protocol`) is a small hand-rolled
binary format — length-prefixed fields via `DataOutputStream`/
`DataInputStream` — not JSON or protobuf. Packets travel over
small-payload links (BLE-backed Nearby connections, and potentially
SMS-sized chunks), so a compact, dependency-free format keeps framing
predictable. Round-trip correctness is covered by
`PacketCodecTest`/`DeviceIdentityCodecTest`.

### What gets signed

`PacketSigning.canonicalBytes(packet)` covers everything **except**
`hopCount`, because relays legitimately increment that field in transit —
the signature must still verify unchanged at the final recipient.
Verification for `DATA`/`ACK` packets uses the sender's public key looked
up from `IdentityDirectory` (i.e., the contacts table); `HELLO` packets
are self-verified against the key embedded in their own payload (see
`docs/SECURITY.md`).

## HELLO — the identity handshake

A `HELLO` packet's `ciphertext` field carries a `DeviceIdentityCodec`-encoded
`DeviceIdentity` (device id, display name, signing + agreement public
keys, capabilities) — unencrypted, because this information is meant to
be shared, but still signed. `RoutingEngine` sends one automatically to
every peer as soon as a transport reports it connected. This is what
populates the contacts table with a real, cryptographically self-consistent
peer identity — nothing about discovered peers is fabricated UI state.

## ACK

An `ACK`'s only meaningful payload is `acknowledgedMessageId`; it is
routed exactly like a `DATA` packet (subject to the same TTL/hop
bounds/dedup) addressed back to the original sender. Receiving it is what
transitions a message from `SENT_TO_PEER`/`RELAYING` to `DELIVERED` — a
relay accepting a packet is never conflated with the recipient actually
having it.

## SMS envelope

When a message is explicitly sent by SMS (see `docs/ROUTING.md`), the
whole encoded packet is base64'd and prefixed with `SINKv1:` before being
split into segments via `SmsManager.divideMessage`. A message that would
need more than 6 segments is refused with a clear reason rather than
silently truncated (`SmsTransport.SMS_MAX_SEGMENTS`).
