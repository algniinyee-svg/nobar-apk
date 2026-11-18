# NOBAR Native Android migration

The supplied repository is preserved. A separate `native-android/` Android Gradle project has been added so the existing Capacitor application and production backend are not overwritten during migration.

## Architecture chosen

- Native Android screens: Kotlin + Jetpack Compose.
- Portable business rules: JavaScript executed by Rhino (no browser DOM dependency).
- HTTP/WebSocket transport: OkHttp.
- Direct media playback: AndroidX Media3.
- Existing backend and PostgreSQL: unchanged; existing endpoints and WebSocket message names are retained.

## Build and tests

See `native-android/README.md`. The portable JS rule tests can run immediately with:

```bash
node native-android/tests/core.test.js
```

A GitHub Actions workflow is included at `.github/workflows/build-native-apk.yml` to compile the Android project and upload a debug APK artifact.

## Release gate

This is an initial native migration implementation, not a completed feature-parity release. The detailed source-based feature matrix is `native-android/FEATURE-MATRIX.md`. YouTube playback, WebRTC, FCM, social/inbox/schedules, wallpaper, subtitles/translation, recap and some room moderation/queue flows remain incomplete. A native APK has not been built or device-tested in this environment because Android SDK/Gradle are not installed here. Do not distribute this alpha as a replacement for the current production app until the pending rows are implemented and regression-tested.
