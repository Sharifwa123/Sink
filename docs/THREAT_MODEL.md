# Threat Model

Sink is not advertised as absolutely secure. This document says plainly
what it defends against and what it doesn't.

## 1. Malicious relay

**Protected.** A relay never has the message-encryption key, only
`ciphertext` + routing metadata (`docs/SECURITY.md`). It can drop, delay,
or duplicate a packet, but cannot read or undetectably modify it (AES-GCM
is authenticated; a modified ciphertext fails to decrypt at the real
recipient rather than producing altered plaintext).

**Not protected**: a relay can see *that* a packet of a given size passed
through it, when, and its (unverified, by the relay) claimed sender/
destination ids — see "Traffic analysis" and "Metadata leakage" below.

## 2. Fake peer / spoofed identity

**Partially protected.** `RoutingEngine` never treats a `DATA` packet as
authentic without verifying its signature against a public key already
in the contacts directory. The first time two devices meet, that key
comes from the peer's own self-signed `HELLO` (trust-on-first-use) — see
`docs/SECURITY.md` for exactly what that does and doesn't prove. A user
who verifies a contact's safety number out-of-band has strong assurance
for that contact going forward; a user who never verifies is relying on
TOFU alone, same as most consumer E2E messengers on first contact.

## 3. Message replay

**Protected.** Every packet carries a unique `messageId`; `MessageIdCache`
means a captured-and-resent packet is dropped as a duplicate, not
reprocessed as new. TTL additionally bounds how long a captured packet
could be replayed for even before considering dedup.

## 4. Packet tampering

**Protected** for the parts that matter: AES-GCM's authentication tag
covers the ciphertext, and the sender's ECDSA signature covers the
header/ciphertext/encryption-metadata (excluding `hopCount`, which relays
legitimately mutate — see `docs/NETWORK_PROTOCOL.md`). A tampered packet
fails decryption or signature verification and is dropped.

## 5. Traffic analysis

**Not protected.** Sink does not pad packet sizes, does not add cover
traffic, and packet metadata (sender/destination ids, timing, size) is
visible to anyone who can observe the radio link or who relays the
packet. An observer positioned to see multiple hops could potentially
correlate traffic patterns. This is a known, disclosed gap — mitigating
it (padding, mixing, onion-style multi-hop encryption of routing
metadata) is future work, not attempted here.

## 6. Device theft

**Partially protected.** The signing private key is Keystore-hardware-backed
and non-exportable. The agreement private key is wrapped by a
hardware-backed AES key at rest (`docs/SECURITY.md`), so it isn't
plaintext-readable by extracting app files. **Not protected**: Sink does
not currently gate identity/message access behind biometric or PIN
re-authentication within the app itself — if the device is unlocked, the
app's own data is not additionally locked. This is a reasonable near-term
enhancement (`WrappingKeyProvider`'s Keystore key could require user
authentication) not implemented in this MVP.

## 7. Compromised device

**Not protected**, and cannot be, by any messaging app: if the device
itself is compromised (malware with app-data access, root), the attacker
can read plaintext messages as the legitimate user reads them, and could
potentially exfiltrate the wrapped agreement key material and, if they
also compromise Keystore-level operations, decrypt it. No cryptographic
design defends the honest endpoint against a compromised endpoint.

## 8. Spam / unwanted messages

**Protected.** `PeerRateLimiter` bounds packets accepted per
directly-connected neighbor per time window, independent of the packet's
claimed sender. Blocking a contact (`ContactEntity.isBlocked`) makes
`RoomIdentityDirectory` withhold that contact's keys entirely, so
`RoutingEngine` can't verify their signature and drops their `DATA`
packets exactly as it would an unknown sender's — enforced at the routing
layer, not only hidden in the Contacts UI.

## 9. Denial of service

**Partially protected.** `PeerRateLimiter` and the 1MB packet-size bound
(`docs/ROUTING.md`) bound the cost a single malicious/malfunctioning
neighbor can impose. **Not protected**: a coordinated multi-device attack
against a single victim (many distinct neighbors, each under the
per-neighbor rate limit) is not specifically mitigated.

## 10. Sybil-style peer abuse

**Not specifically protected.** Creating a new Sink identity costs
nothing but generating a key pair — there's no proof-of-work, invitation,
or other Sybil resistance. In a small, largely trust-network-based mesh
(people you've verified) this is a lower-severity gap than it would be in
an open, anonymous network, but it's not mitigated at the protocol level.

## 11. SMS interception / limitations

**Not protected by Sink; inherited from SMS itself.** SMS is not
end-to-end encrypted at the carrier/network level in the way the mesh
path is — the packet Sink sends over SMS is itself still Sink's own
encrypted payload (base64'd), so message *content* is still protected by
the same AES-GCM encryption; but SMS metadata (sender/recipient phone
number, timing) is visible to the carrier and to standard SMS
interception techniques (SS7-level attacks, a malicious/compelled
carrier) exactly as it is for any SMS. Sink's UI explains SMS is a
fallback, not the primary path, in part for this reason.

## 12. Metadata leakage

**Not protected**, beyond what's stated in "Traffic analysis" above. A
relay learns the claimed sender/destination device ids and approximate
message size and timing for every packet it forwards, by design (that's
what routing requires). Device ids are pseudonymous (public-key
fingerprints, not phone numbers or names), which limits — but does not
eliminate — what a relay can infer.

---

**In one sentence**: Sink protects message *content* end-to-end and
resists casual tampering/replay/flooding; it does not protect against a
compromised endpoint, does not hide routing metadata from relays, and
treats first-contact identity as trust-on-first-use unless the user
verifies a contact's safety number out-of-band.
