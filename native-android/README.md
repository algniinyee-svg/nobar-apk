# NOBAR Native Android — V12 buildable source project

This is a native Android migration project. The UI is Kotlin + Jetpack Compose; portable business rules are JavaScript executed with Rhino. Networking, media, WebRTC, Android lifecycle and notifications are implemented in Kotlin. The existing NOBAR Node.js backend, database, HTTP endpoints and WebSocket protocol are intended to remain unchanged.

## Included implementation

- Native login, registration, guest entry, room entry and room UI.
- Existing HTTP authentication, WebSocket room state, chat/history, queue, proposals, reactions and host-control message formats.
- Eight-member room cap and JavaScript core rules.
- Media3 playback for direct HTTP(S) media and YouTube IFrame playback in a dedicated WebView surface with native controls and synchronized seek.
- Firebase Cloud Messaging registration, notification handling and deep-link invitation prefill (requires the matching Firebase project configuration and backend service account).
- Native polls, countdown, room controls, WebRTC audio/video calls, authenticated STUN/TURN retrieval and remote video tiles.
- Social statistics, schedule listing/creation/deletion, room invitations and live inbox invitation cards.
- Native color themes, performance profiles and account wallpaper upload/preview/delete.
- Room recap display and Android Sharesheet export.

## Build requirements

- Android Studio with Android SDK Platform 35 and Build Tools installed.
- JDK 17.
- Gradle 8.9 (Android Gradle Plugin 8.7.3).
- Internet access on the build machine to resolve Maven/Google dependencies.

Open the `native-android` directory in Android Studio and allow Gradle sync. Or from that directory:

```bash
python3 scripts/verify-source.py
bash scripts/test-js.sh
gradle --no-daemon clean testDebugUnitTest assembleDebug
```

The debug APK is produced at:

`app/build/outputs/apk/debug/app-debug.apk`

To install on a connected Android device with USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Convenience scripts are also provided:

```bash
scripts/build-debug.sh
scripts/install-debug.sh
```

### Cloud build

A GitHub Actions workflow is included at `.github/workflows/android-debug-apk.yml`. Push the project to a GitHub repository and run **Actions → Android debug APK → Run workflow**. It provisions JDK 17, Android SDK and Gradle 8.9, runs the source checks and Android unit tests, builds the debug APK, and uploads it as the `nobar-debug-apk` artifact. The workflow is not run from this chat environment.

## Firebase Cloud Messaging

1. Create/use a Firebase Android app with package name `com.algani.nobar`.
2. Put its `google-services.json` in `app/google-services.json` (excluded from Git).
3. Configure `FCM_SERVICE_ACCOUNT` on the existing backend.
4. Log in with an account and grant Android notification permission.

Without Firebase configuration, the app remains buildable but push registration/delivery is unavailable.

## Backend and runtime configuration

The production API base URL is configured as `NOBAR_BASE_URL` in `app/build.gradle.kts`. For staging, change this build config value or add a dedicated build flavor before distributing builds. The backend must be reachable over HTTPS/WSS.

## Migration gaps and validation status

This is not yet a production-certified or fully feature-complete replacement for the web client. Native subtitle parsing/translation, persistent direct messages (the original backend has no DM persistence API), full-screen wallpaper background, image-based recap export, and admin dashboard are not implemented. YouTube playback remains provider-rendered in a WebView. WebRTC, push, wallpaper, inbox and scheduling still require integration tests against a live backend and physical devices.

**Current verification in the development container:** source-contract checks and JavaScript assertions can run offline. This environment has no Android SDK, Gradle installation/wrapper, or network DNS access, so Android compilation and APK generation cannot be truthfully claimed here. The supplied GitHub Actions workflow provides a reproducible build path on a provisioned runner.

## Security

No production credentials, Firebase service account, signing keystore or signing passwords are included. Debug APKs are for testing. Configure a private release keystore and secure CI secrets before producing a distributable release APK. Do not commit secrets.
