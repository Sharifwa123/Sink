# Security

This document describes what's actually implemented, including its
limitations. See `docs/THREAT_MODEL.md` for the explicit "protects
against / does not protect against" list.

## Cryptographic primitives

All implemented in `engine/crypto-core` using only the JVM's own
`java.security`/`javax.crypto` (JCA) — no third-party crypto library, and
identical code paths on the JVM (where it's unit tested) and Android
(where the same JCA API is backed by Conscrypt/BoringSSL).

- **Curve**: NIST P-256 (`secp256r1`), not Curve25519/Ed25519. This is a
  deliberate compatibility choice, not an oversight: `KeyPairGenerator`
  with `"EC"` has been available since Android API 1, and Android
  Keystore has supported hardware-backed EC key generation since API 23.
  X25519/Ed25519 support in Android's default providers and in
  AndroidKeystore specifically is inconsistent across OS versions still
  in real-world use at Sink's minSdk (26). If a future minSdk bump makes
  Curve25519 safe to require everywhere, this is the place to revisit.
- **Key agreement**: ECDH per message (see below).
- **Symmetric cipher**: AES-256-GCM (authenticated encryption — a
  tampered ciphertext fails to decrypt rather than silently producing
  garbage plaintext).
- **KDF**: HKDF-SHA256 (RFC 5869), turning a raw ECDH shared secret into
  a uniform 256-bit AES key.
- **Signatures**: ECDSA over SHA-256.

## Message encryption (`EciesCipher`)

A minimal integrated encryption scheme, conceptually equivalent to ECIES:
sender generates a fresh **ephemeral** EC key pair per message, ECDH's it
against the recipient's long-term agreement public key, derives an AES
key via HKDF (salted with the ephemeral public key), and encrypts with
AES-256-GCM. The ephemeral public key and GCM nonce travel with the
ciphertext (`EncryptionMetadata`) — a relay sees only these plus routing
metadata, never plaintext.

**Forward secrecy is partial, not full**: compromise of the sender's
ephemeral private key (which exists only for the lifetime of encrypting
one message and is never stored) doesn't expose that message, but this is
not a full Double Ratchet — there's no post-compromise security if the
recipient's long-term agreement private key is later compromised, past
messages encrypted to that key become decryptable if they were also
captured. A ratcheting session protocol is a reasonable future
enhancement; it was out of scope for this MVP.

## Device identity and key storage

- **Signing key pair**: generated once, on first identity creation, as a
  non-exportable Android Keystore key (`AndroidSigningKeyProvider`),
  StrongBox-backed where the device supports it (falls back to the
  TEE-backed key on `StrongBoxUnavailableException`). Only sign/verify
  operations touch it — the private key material never leaves secure
  hardware and is never readable by the app process.
- **Agreement key pair**: Android Keystore's ECDH (`PURPOSE_AGREE_KEY`)
  requires API 31+; Sink's minSdk is 26. To still support ECDH on
  API 26–30, this key pair is generated in software
  (`SinkKeyPairs.generate()`), and its private key bytes are encrypted
  ("wrapped") with a separate hardware-backed AES-256-GCM Keystore key
  (`WrappingKeyProvider`) before being written to disk
  (`EncryptedIdentityStore`, plain `SharedPreferences` — safe because
  what's stored is already ciphertext, never plaintext key material).
  **This is envelope encryption, not full hardware custody**: the
  unwrapped agreement private key exists briefly in process memory
  whenever a message is encrypted/decrypted for/from a peer. This is
  explicitly a compatibility tradeoff for minSdk 26, not an oversight —
  documented here rather than implied to be equivalent to the signing
  key's protection.
- **Device id**: the fingerprint (`Fingerprint.of`, SHA-256 of the
  encoded signing public key, rendered as decimal groups) of the signing
  public key — never a phone number, never re-derived/regenerated
  silently after first creation.
- **`allowBackup="false"`** in the manifest: Sink's local database and
  identity store are not included in Android's automatic cloud backup,
  since neither is encrypted for that specific transport.

## Contact trust — HELLO handshake and safety numbers

On first contact, two devices exchange a self-certifying `HELLO` packet
(see `docs/NETWORK_PROTOCOL.md`): the embedded signing public key must
hash to the claimed device id, and must have produced the packet's own
signature. **This is trust-on-first-use (TOFU)**: it proves internal
consistency of the claim (nobody else could have forged this exact
combination without that private key), not that the human on the other
end is who they claim to be, and not protection against an attacker who
was already in the middle of the very first exchange.

The Contacts screen shows each contact's fingerprint ("safety number")
and lets the user mark a contact `isVerified` after comparing it with the
contact out-of-band (in person, over a trusted channel). Sink never
claims a contact is "secure" merely because messages to them are
encrypted — encryption is automatic and universal; verification is a
separate, human, optional step, and the UI does not conflate the two.

## What's logged

`SinkLogger` (`core:logging`) has no parameter for message bodies, SMS
content, or key material — only identifiers, states, and exception class
names. `BuildConfig.DEBUG_LOGGING` gates debug-level logs to debug builds
only.

## Network transport security

- `network_security_config.xml` disables cleartext traffic app-wide —
  any future internet feature must use TLS.
- Local mesh links (Nearby Connections) are not additionally
  TLS-wrapped by Sink; message confidentiality on the mesh comes entirely
  from the end-to-end `EciesCipher` layer described above, which is why
  it's applied per-message rather than relying on transport security.
