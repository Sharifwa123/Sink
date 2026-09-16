# Release

## Versioning

`app/build.gradle.kts`: `applicationId = "com.sharif.sink"`,
`versionCode = 1`, `versionName = "0.1.0"`. Bump both on every release;
`versionCode` must strictly increase for Play Store uploads.

## Signing

Release builds are **not signed by default** — `keystore.properties` is
never committed (see `.gitignore`) and its absence means
`android.signingConfigs` simply omits the `release` config, so
`assembleRelease` produces an unsigned APK/AAB rather than failing. To
produce a signed release build:

1. Generate a keystore (once, keep it safe — losing it means you can
   never update the app under the same `applicationId` again):
   ```bash
   keytool -genkey -v -keystore sink-release.jks -keyalg RSA -keysize 2048 \
     -validity 10000 -alias sink
   ```
2. Create `keystore.properties` at the repo root (never commit it):
   ```properties
   storeFile=/absolute/path/to/sink-release.jks
   storePassword=...
   keyAlias=sink
   keyPassword=...
   ```
3. `./gradlew :app:bundleRelease` (for Play Store) or
   `:app:assembleRelease` (for a directly-distributable APK).

## R8 / ProGuard

`isMinifyEnabled = true` and `isShrinkResources = true` on the `release`
build type. Rules live in `app/proguard-rules.pro`; see that file's
comments for what does and doesn't need explicit keep rules and why (most
library consumer rules are bundled automatically; Sink's own wire codecs
use no reflection).

## No debug secrets, no test endpoints

- `debug` build type gets an `applicationIdSuffix = ".debug"` so debug and
  release builds can be installed side by side without colliding.
- `BuildConfig.DEBUG_LOGGING` (`core:logging`) is `false` in release
  builds — debug-level logs compile out entirely, never just get filtered
  at runtime.
- There is no backend/API endpoint configuration to leak — Sink's core
  messaging has no server dependency (see `docs/ARCHITECTURE.md`), and
  the internet transport is currently an honest stub
  (`docs/IMPLEMENTATION_STATUS.md`).

## App icon, branding

The launcher icon (`app/src/main/res/mipmap-anydpi-v26/`) is a vector
adaptive icon (three connected nodes — the mesh-relay concept — in the
brand teal on the brand navy background), not a placeholder. minSdk 26
means every supported device gets the adaptive icon; no legacy PNG
fallback mipmaps are needed.

## Privacy policy / terms

Sink's core functionality requires no account and collects no data off
the device (see `docs/SECURITY.md`, `docs/THREAT_MODEL.md`). A privacy
policy and terms-of-service document are still required for Play Store
listing regardless of how little data is collected — these are business/
legal documents SHARIF TECHNOLOGIES needs to author and host; this
repository provides the accurate technical description
(`docs/SECURITY.md`) they should be based on, but does not itself contain
a legal privacy policy page.

## Before every release, verify

1. `cd engine && ./gradlew test` is green (43 tests as of this writing;
   see `docs/TESTING.md`).
2. `./gradlew :app:assembleDebug` succeeds in an environment with the
   Android SDK and network access to Google's Maven repository (this
   project's own development sandbox could not verify this — see
   `docs/ANDROID_LIMITATIONS.md` — so this is the first real compile
   check a release process must perform).
3. Manual device testing (`docs/DEVICE_TESTING.md`) for anything touched
   since the last release.
4. `docs/IMPLEMENTATION_STATUS.md` reflects the current state honestly.
