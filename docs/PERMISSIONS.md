# Permissions

Every permission Sink can request, why, and when. Nothing is requested
"because it might be useful later" — see `docs/ANDROID_LIMITATIONS.md`
for the exact API-level branching.

| Permission bundle | Manifest permissions (varies by API level) | Why | When requested |
|---|---|---|---|
| `NEARBY_DEVICES` | `BLUETOOTH_SCAN`/`ADVERTISE`/`CONNECT`, `NEARBY_WIFI_DEVICES`, or `ACCESS_COARSE_LOCATION` pre-Android 12 | Discover and connect to nearby Sink devices for local mesh messaging | Onboarding's "Find nearby Sink users" step (skippable), and again from the Discovery screen if skipped or later revoked |
| `NOTIFICATIONS` | `POST_NOTIFICATIONS` (API 33+ only; granted at install pre-33) | Let the user know about incoming/delivered messages and mesh status while the app is backgrounded briefly | Not currently requested via a dedicated runtime prompt in this build — surfaced by the OS the first time a notification would be shown; a proactive contextual prompt is a reasonable follow-up (see `docs/IMPLEMENTATION_STATUS.md`) |
| `SEND_SMS` | `SEND_SMS` | Send a message as a fallback when no mesh/internet route exists, only after the user explicitly chooses "Send by SMS" | At the moment the user taps "Send by SMS" on a specific message — never earlier |

## Always-declared, non-runtime permissions

- `INTERNET`, `ACCESS_NETWORK_STATE` — for the optional internet
  transport path (currently a stub, see `docs/IMPLEMENTATION_STATUS.md`)
  and connectivity awareness. Not sensitive/runtime permissions.
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE` — required by Nearby
  Connections' Wi-Fi-based strategy internals.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` — for
  `MeshForegroundService`, which only runs while the app is visible (see
  `docs/ANDROID_LIMITATIONS.md`).

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
