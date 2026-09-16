# Manual Multi-Device Test Procedure

The automated suite proves the routing/crypto logic in simulation
(`docs/TESTING.md`). This procedure verifies the same behavior against
real radios, real Android permission dialogs, and real battery/lifecycle
behavior — none of which the simulator can stand in for.

## Minimum setup

Three Android devices (API 26+), each with Sink installed and onboarded
with a distinct display name: **A**, **B**, **C**.

## Test 1 — direct delivery

1. Bring A and B physically close enough to discover each other.
2. On A, open Discovery, wait for B to appear, tap it to add as a
   contact (this triggers the HELLO handshake, then verify each device
   shows the other's fingerprint on the Contacts screen).
3. From A's contact list, open a chat with B and send a text message.
4. **Expect**: message shows `Sending…` then `Delivered` on A; appears in
   B's chat immediately.

## Test 2 — relay through B

1. Place C out of A's direct range but within B's range (B relays for A→C).
2. Ensure A, B, and C have already exchanged contacts pairwise (A↔B, B↔C)
   so keys are known — a direct A↔C HELLO isn't required for a relay
   payload to route (RoutingEngine only needs A and C to have *previously
   exchanged keys with each other* to encrypt/verify — if A and C have
   never met, they must exchange keys through some path before messaging
   can succeed; verify this by having A add C as a contact once B has
   relayed at least one HELLO/introduction, or by directly discovering
   each other briefly first).
3. Send a message from A addressed to C.
4. **Expect**: on A, message state moves through `Sending…` →
   `Relaying through 1 device` → `Delivered`. On A's message details
   screen (tap the message), hop count should read `1`.

## Test 3 — relay disappears mid-route

1. With the topology from Test 2 established, turn off Wi-Fi/Bluetooth on
   B (or force-close Sink on B, or move B out of range of both A and C).
2. Send another message from A to C.
3. **Expect**: the message stays `Queued` on A (never silently disappears,
   never marked `Failed` immediately) — check via the chat screen or
   Diagnostics ("Queued messages" count).
4. Bring B back into range of both A and C (re-enable radios / relaunch
   Sink on B).
5. **Expect**: within moments (B reconnecting triggers an immediate retry,
   not just the 15-minute periodic sweep), the queued message delivers
   and A's UI updates to `Delivered`.

## Test 4 — SMS fallback

1. On a device with no nearby Sink devices and no route to a specific
   contact, compose a message anyway (it will sit `Queued`, then
   eventually `Failed` once retries exhaust — or trigger this immediately
   by disabling all radios first).
2. Tap "Send by SMS" on the failed message.
3. Grant the `SEND_SMS` runtime permission prompt if this is the first
   use.
4. **Expect**: an actual SMS is sent from the device (verify in the
   device's own SMS app / carrier records) containing a `SINKv1:`-prefixed
   payload — not human-readable text, since it's still the encrypted
   packet. If the recipient's Sink app is also running with SMS receiving
   registered, the message should appear in their chat once
   `SmsReceiver` parses the incoming SMS.

## Test 5 — duplicate suppression under real conditions

1. With A, B, and C all in range of each other simultaneously (so more
   than one path exists), send a message from A to C.
2. **Expect**: C's chat shows the message exactly once, not twice, even
   though it may have arrived via multiple paths.

## Test 6 — battery/background behavior sanity check

1. Send Sink to the background on all three devices.
2. **Expect**: the "Sink is looking for nearby devices…" foreground
   notification disappears shortly after backgrounding (per
   `docs/ANDROID_LIMITATIONS.md`, Sink does not maintain mesh connections
   indefinitely in the background) — this is expected behavior, not a
   bug, and should be explained if a field tester flags it.

## Recording results

For each test, record: Android version, device model, distance/obstacles
between devices, and the exact delivery-state sequence observed in the UI
and (if available) via the Diagnostics screen.
