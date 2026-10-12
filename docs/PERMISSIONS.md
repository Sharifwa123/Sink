# Permissions

Every permission Sink can request, why, and when. Nothing is requested
"because it might be useful later" — see `docs/ANDROID_LIMITATIONS.md`
for the exact API-level branching.

| Permission bundle | Manifest permissions (varies by API level) | Why | When requested |
|---|---|---|---|
| `NEARBY_DEVICES` | `BLUETOOTH_SCAN`/`ADVERTISE`/`CONNECT`, `NEARBY_WIFI_DEVICES`, or `ACCESS_COARSE_LOCATION` pre-Android 12 | Discover and connect to nearby Sink devices for local mesh messaging **over Bluetooth and Wi-Fi directly** — "Wi-Fi" here includes Wi-Fi Direct/hotspot-style ad-hoc links and same-network Wi-Fi LAN, whichever Nearby Connections picks automatically; never mobile data, and never the internet (see `docs/ANDROID_LIMITATIONS.md`) | Onboarding's "Find nearby Sink users" step (skippable), and again from the Discovery screen if skipped or later revoked. Copy throughout the app names Bluetooth and Wi-Fi explicitly rather than a vague "nearby devices" label, and the Diagnostics screen shows each radio's grant status individually. |
| `NOTIFICATIONS` | `POST_NOTIFICATIONS` (API 33+ only; granted at install pre-33) | Let the user know about incoming messages and mesh status while the app is backgrounded briefly | Onboarding's "Get notified about new messages" step, right after the nearby-permission step (skippable via "Not now") |
| `SEND_SMS` | `SEND_SMS` | Send a message as a fallback when no mesh/internet route exists ("Send by SMS"), or send a plain invite SMS to a phone number added from Contacts' **+** button | At the moment the user taps "Send by SMS" on a specific message, or "Send invite via SMS" when adding a contact by phone number — never earlier |

## Always-declared, non-runtime permissions

- `INTERNET`, `ACCESS_NETWORK_STATE` — for the optional internet
  transport path (currently a stub, see `docs/IMPLEMENTATION_STATUS.md`)
  and connectivity awareness. Not sensitive/runtime permissions.
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` — required by Nearby
  Connections' Wi-Fi-based strategy internals.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` — for
  `MeshForegroundService`, which runs for as long as Sink stays in
  Recents (not just while the app is on-screen) and stops when the user
  swipes it away (see `docs/ANDROID_LIMITATIONS.md`).

## Graceful denial handling

- **Nearby permission denied**: Discovery shows "Nearby device permission
  is required" with an inline "Allow" button (re-requesting) rather than
  a blank or broken screen; onboarding lets the user tap "Not now" and
  continue rather than blocking progress.
- **SMS permission denied**: `SmsTransport.send()` returns a clear
  `Failed("SMS permission not granted")`, surfaced in the chat screen's
  error banner — never a silent no-op.
- Sink does not currently deep-link to system Settings for a permanently
  denied permission; that's a reasonable near-term UX improvement, not
  yet implemented.
