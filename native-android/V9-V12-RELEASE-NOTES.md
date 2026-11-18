# NOBAR native migration — V9 through V12

This archive is cumulative from V8 and carries four implementation batches:

## V9 — Preferences
- Six native accent themes: Sinema, Fajar, Samudra, Hutan, Anggur, and Mono.
- Persisted Auto / Hemat / Penuh performance preference.
- Hemat mode skips remote video tile rendering while retaining the call audio path.

## V10 — Account wallpaper
- Android image picker accepts common image formats.
- Images are resized to a 1600 px longest edge and compressed to JPEG under the existing 800 KB server limit.
- Upload, retrieve, preview, and delete use the original `/api/wallpaper` endpoint; no server changes.

## V11 — Live inbox
- A separate home-presence WebSocket sends the existing authenticated `hello` and `vis` messages.
- Incoming `invite` events are surfaced in the home screen; users can hand the room code and password into the join form or dismiss the invitation.
- Reconnect uses bounded backoff. Push while the app process is terminated still depends on FCM configuration.

## V12 — Room recap
- Requests the original `recapreq` event and consumes the existing `recap` payload.
- Displays watch duration, participants, videos, message count, and reactions.
- Shares a text recap using Android Sharesheet.

## Verification and limitations
- `python scripts/verify-source.py`: source-contract checks passed.
- `bash scripts/test-js.sh`: 10 JavaScript assertions passed.
- ZIP integrity is checked before delivery.
- No Gradle wrapper/system Gradle or Android SDK is available in this environment. Therefore this archive is **source-verified, not APK-built**. It has not been tested against a live backend, Firebase delivery, or physical Android devices.
- Native subtitle parsing/translation, persistent direct messages (no original DM API exists), full-screen wallpaper background, image-based recap export, and admin dashboard remain incomplete.
