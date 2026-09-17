# Routing

## The algorithm: TTL/hop-bounded controlled flooding

A node that receives a packet not addressed to itself forwards a copy to
every currently reachable neighbor **except the one it arrived from**,
until `maxHops` is reached or the packet's TTL expires. Duplicate
delivery — inevitable once there's more than one path, or once retries
overlap with a packet already in flight — is suppressed by a per-device
`MessageIdCache` (`engine/mesh-engine`), so a relay never reprocesses or
re-forwards something it's already seen.

## Why this and not a shortest-path/topology-aware protocol

- Sink has no stable network-layer addressing scheme across an
  intermittently-connected mesh — a device's reachable neighbor set
  changes as people walk around, phones sleep, and radios reconnect.
  Building and keeping a full topology graph current across a
  fast-changing, disconnection-prone edge set is extra complexity not
  worth building for the current scope.
- Controlled flooding bounded by TTL and hop count is a standard,
  well-understood strategy for delay-tolerant / opportunistic networks —
  it needs no coordination protocol between nodes and degrades
  gracefully (a lost relay just means one fewer path, not a broken
  routing table).
- The dedup cache means flooding's redundancy costs bandwidth and
  battery on retransmission, not correctness — a message reaching a node
  via two paths is a non-event, not a bug (see
  `MeshRoutingSimulationTest."duplicate copies arriving via redundant paths..."`).

This is documented here as a deliberate MVP-scope decision, not an
oversight: a future version could add route advertisement/caching to
reduce redundant transmissions once the mesh's actual behavior in the
field is better understood.

## Store-and-forward and retry

Every outgoing message keeps a `RetryBookkeeping` entry (`RoutingEngine`)
until it's ACKed, expires, or exhausts `RetryPolicy.maxAttempts` (default
8, exponential backoff up to 15 minutes). Two independent triggers drive
retries:

1. **Immediately**, when a new peer connects (`retryQueuedMessagesNow`) —
   a message that never had *any* route yet deserves an instant attempt,
   not a wait for its next scheduled backoff.
2. **Periodically**, via `MeshRetryWorker` (WorkManager, every 15 minutes —
   its minimum period), as a safety net for the case where the app
   process was killed and later restarted with no fresh peer-connect
   event to trigger a sweep.

A message that exhausts automatic retries is marked `FAILED`, but its
packet is deliberately **not** discarded (`RoutingEngine.packetById`) —
that's exactly when a user might tap "Send by SMS", and that resend needs
the original signed/encrypted packet.

## SMS is never part of automatic routing

`TransportManager.MESH_BROADCAST_KINDS` excludes `SMS` from
`reachablePeers()`/`broadcast()` entirely. SMS costs the user money,
reveals a phone number, and must always be an explicit, user-confirmed
action. The only way a packet goes out over SMS
is `RoutingEngine.sendViaSpecificTransport(messageId, TransportKind.SMS)`,
called directly by the chat UI when the user taps "Send by SMS" on a
message with no other route.

## Abuse resistance

- **`PeerRateLimiter`**: every packet is rate-limited by the *immediate
  neighbor that handed it over*, not by its claimed (unverified at that
  point) sender id — checked before any crypto or dedup bookkeeping, so a
  flooding neighbor is cheap to reject.
- **Packet size bound**: an oversized `ciphertext` (`> 1MB`) is dropped
  before any further processing.
- Both are exercised in `MeshRoutingSimulationTest."a flooding neighbor is
  rate limited..."`.
