# Release

## CI: `.github/workflows/android-build.yml`

This is the project's build/test gate. On every push and PR:

1. **`engine-tests`** — runs the full 43-test `engine/` suite (no Android
   SDK needed).
2. **`build-debug-apk`** — installs the Android SDK (`android-actions/setup-android`)
   and runs `./gradlew :app:assembleDebug`, uploading the resulting APK
   as a workflow artifact. This is the real, first compile check this
   codebase gets.
3. **`build-release-apk`** — only runs if the repository variable
   `HAS_RELEASE_SIGNING` is set to `true` (Settings → Secrets and
   variables → Actions → Variables), and reads the four secrets below to
   reconstruct `keystore.properties` at build time. Never enable this
   without also adding the secrets — the job reconstructs the keystore
   from them into the runner's temp directory, never commits anything.
4. **`publish-debug-release`** — on every push to the default branch,
   publishes the debug APK to a permanent, public GitHub Release tagged
   `latest-debug` (see "Downloads for visitors" below). Workflow-run
   artifacts alone aren't a real download link once the repo is public:
   they expire and require a GitHub sign-in to fetch.
5. **`publish-release`** — same idea, but for the signed release APK, only
   once `HAS_RELEASE_SIGNING` is configured. Publishes/updates a versioned
   release (`vX.Y.Z`, read from `app/build.gradle.kts`'s `versionName`)
   and marks it as the repository's "latest" release.

## Downloads for visitors

Now that the repository is public, anyone can grab a build without
signing into GitHub or touching Android Studio, from the Releases page:
`https://github.com/Sharifwa123/Sink/releases`.

- **`latest-debug`** always points at the newest successful build of the
  default branch — a rolling/continuous release, republished on every
  push (its git tag is deleted and recreated each time, so old debug APKs
  don't pile up as separate releases). Debug-signed, for trying Sink out;
  not a production release.
- Once release signing is configured (below), each version gets its own
  permanent, versioned release (`v0.1.0`, `v0.2.0`, ...) with the signed
  APK attached, and the newest one is marked "Latest" on the Releases
  page.

To enable signed release builds in CI, add these **repository secrets**
(Settings → Secrets and variables → Actions → Secrets):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 sink-release.jks` output |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | the key alias (e.g. `sink`) |
| `RELEASE_KEY_PASSWORD` | the key password |

...and set the repository **variable** `HAS_RELEASE_SIGNING` to `true`.

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
  (`docs/IMPLEMENTATION_STATUS.md`). The one outbound call Sink makes on
  its own is the optional, disclosed GitHub releases check described in
  `docs/SECURITY.md` — a hardcoded public API URL, no credentials.

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

1. The `android-build` GitHub Actions workflow is green on the commit
   being released (both `engine-tests` and `build-debug-apk`).
2. Manual device testing (`docs/DEVICE_TESTING.md`) for anything touched
   since the last release.
3. `docs/IMPLEMENTATION_STATUS.md` reflects the current state honestly.
