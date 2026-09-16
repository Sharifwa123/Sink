# Sink release R8/ProGuard rules.
#
# Most of what would normally need explicit keep rules here (Room, Hilt/Dagger,
# WorkManager, Play Services Nearby) already ships its own consumer ProGuard
# rules bundled in each library's AAR, applied automatically. The rules below
# cover what's specific to this app.

# Manifest-declared components (Activities, Services, BroadcastReceivers) are
# kept automatically by the Android Gradle Plugin's default rules — no entry
# needed for MainActivity, MeshForegroundService, or SmsReceiver here.

# Sink's own wire codecs (PacketCodec, DeviceIdentityCodec) and crypto classes
# use no reflection — they read/write explicit fields in a fixed order — so
# they need no keep rules to survive minification correctly.

# Kotlin coroutines' internal use of continuations can confuse stack traces
# under R8; keep debug metadata so a crash report is still readable.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Suppress notes for optional/reflective code paths that some transitive
# dependencies reference defensively; nothing here is exercised at runtime.
-dontnote kotlinx.coroutines.**
