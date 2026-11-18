# NOBAR feature migration matrix

Status is based on source files included in the supplied `nobar-main.zip` and the native implementation currently in this branch.

| Original feature | Source evidence | Native migration status |
|---|---|---|
| Login | `public/app.js`, `/api/login` | Initial Compose flow implemented |
| Registration | `public/app.js`, `/api/register` | Initial Compose flow implemented; recovery code surfaced |
| Guest login | `public/app.js`, `/api/guest` | Initial Compose flow implemented |
| Session restoration | browser localStorage | SharedPreferences token restoration implemented |
| Room join | `public/app.js`, WS `join` | Implemented |
| Room capacity (8) | `server.js` MAX=8 | Server preserved; native UI displays capacity |
| Room membership/host mode | WS `members` | Implemented |
| Chat/history | WS `chat`, `history` | Implemented |
| Direct URL media | `public/app.js`, WS `act` | Media3 initial implementation |
| YouTube playback | YouTube IFrame API in `public/app.js` | Integrated through the official YouTube IFrame player hosted in a dedicated WebView surface; not a native YouTube SDK player |
| Playback play/pause/seek | WS `act`, `state` | Initial play/pause/seek protocol support; advanced drift correction not yet ported |
| Duo proposals | WS `proposal`, `vote` | Initial approval UI implemented |
| Queue | WS `queue`, `qadd`, `qrm`, `qnext` | Queue display, add/remove controls, and automatic next-item request implemented |
| Join-request moderation | WS `joinreq`, `joinans` | Host approval/rejection controls implemented |
| Host kick/transfer/mute | WS `kick`, `givehost`, `mute` | Native host controls implemented |
| Reactions | WS `react` | Initial reaction send action implemented |
| WebRTC voice/video | `public/rtc.js`, `/api/ice`, WS `sig` | Native WebRTC audio/video, TURN configuration, remote video tiles, mic/camera controls; device-level tests pending |
| Push notifications | `public/push.js`, `fcm.js` | Native FCM service, token register/refresh/unregister and notification handling implemented; Firebase/backend configuration and device delivery test pending |
| Inbox/invitations | `inbox.js`, `public/invite.js` | Native live invitation inbox via `hello`/`vis`, incoming invite card, room handoff; background push depends on Firebase setup |
| Social profile/stats/badges | `social.js`, `public/fun.js`, `public/home.js` | Native activity stats and earned badges implemented |
| Schedules | `public/fun.js` | Native list/create/delete using existing API implemented |
| Wallpaper | `wallpaper.js`, `public/wp.js` | Native picker, JPEG compression, upload, preview, download, and delete implemented; home preview only |
| Themes | `public/settings.js`, `public/glass.js` | Native six-color accent themes and performance profiles; full browser glass parity not claimed |
| Subtitle/translation | `public/subs.js`, `extras2.js` | Pending native subtitle parser/overlay and translation UI |
| Recap | `public/recap.js`, `extras2.js` | Pending |
| Admin dashboard | `public/admin.html`, `/api/admin/*` | Not migrated; separate protected admin client needed |

Do not describe this migration as feature-complete while any row is pending. The original backend is intentionally unchanged to preserve existing data and protocol compatibility.


## V4 update — native WebRTC engine groundwork

- Added a native WebRTC engine for audio capture and optional Camera2 video capture.
- Added SDP offer/answer and ICE signaling using the original NOBAR `sig` message envelope.
- Added peer lifecycle management, queued ICE candidates, and polite-peer collision handling.
- Added the Google WebRTC Android dependency.

Not yet complete: MainActivity permission/UI wiring, remote video rendering, `/api/ice`
TURN credentials, audio routing/device controls, and real-device multi-peer tests.
Do not mark voice/video calling as feature-complete until those tasks pass.


## V5 integration status

| Capability | Status | Notes |
|---|---|---|
| Native call start/end UI | Source integrated | Requests microphone; camera permission is optional |
| Existing WebSocket signaling | Source integrated | Routes `sig` offer/answer/ICE and host `mute` messages |
| Peer membership lifecycle | Source integrated | Reconciles peer connections with room member IDs |
| Local mic/camera toggles | Source integrated | Camera toggle appears only when a local camera track exists |
| Remote video surface | Not yet implemented | Remote tracks are not rendered in Compose |
| TURN credential fetch | Not yet implemented | Engine still uses public STUN fallback |
| Device call quality / background call tests | Not verified | Requires Android device/emulator and runtime backend |


## V6 update
- [x] Native authenticated `/api/ice` retrieval using the current bearer token
- [x] Parse STUN/TURN URL entries, including username and credential
- [x] Retain public STUN fallback when ICE endpoint is unavailable
- [x] Surface ICE and peer connection state in call status
- [ ] Remote video track rendering in native participant tiles (next integration batch)
- [ ] Android Gradle/device validation (requires Android SDK/device environment)


## V7 — Native remote video rendering

- [x] Receive remote WebRTC video tracks from Unified Plan `onTrack` / `onAddTrack` callbacks.
- [x] Render remote participant video with Android `SurfaceViewRenderer` inside the Compose room UI.
- [x] Label video tiles with the current room member name when available.
- [x] Remove renderers and tracks when peers leave or the call closes.
- [ ] Device-level verification across real networks and Android vendors.
- [ ] Local self-preview, layout optimization for larger rooms, and per-peer volume controls.


## V8 — Native social, schedules, and invitations

- [x] Native activity summary using authenticated `GET /api/mystats`.
- [x] Earned badge display based on backend badge state.
- [x] Schedule listing using authenticated `GET /api/sched`.
- [x] Schedule creation using the existing `POST /api/sched` contract, including invitee usernames.
- [x] Owner-only schedule deletion through `DELETE /api/sched?id=...`.
- [x] Room invitation UI sends the existing WebSocket `invite2` message.
- [ ] Direct-message inbox: the original `inbox.js` implements live invite delivery, not a persistent private-message store/API. A compatible DM backend contract must be designed before claiming DM migration.
- [ ] Live push delivery, schedule reminder delivery, and multi-account end-to-end tests.


## V9 — Native preferences and appearance
- [x] Six native accent themes (Sinema, Fajar, Samudra, Hutan, Anggur, Mono), persisted in `SharedPreferences`.
- [x] Native performance preference (Auto, Hemat, Penuh), persisted across launches.
- [x] Hemat mode suppresses remote video rendering while leaving voice audio active.
- [ ] Pixel-level parity with the browser glass/ambient theme system is not claimed.

## V10 — Account wallpaper
- [x] Native Android image picker for JPEG/PNG source images.
- [x] Resize to a 1600-pixel longest edge and JPEG-compress to the existing server's 800 KB limit.
- [x] Upload, preview, download, and delete through the unchanged `/api/wallpaper` contract.
- [ ] The wallpaper is currently shown in the native home personalization preview; full-screen background treatment is a future visual refinement.

## V11 — Live invitation inbox
- [x] Dedicated authenticated WebSocket presence connection sends the existing `hello` and `vis` messages.
- [x] Reconnect with bounded backoff and clean up on logout/activity destruction.
- [x] Incoming `invite` messages appear in the native home screen with room/password handoff and dismiss action.
- [ ] Delivery while the process is killed relies on the existing FCM push path and valid Firebase/backend configuration.

## V12 — Room recap
- [x] Request recap through the existing `recapreq` WebSocket message.
- [x] Parse the existing `recap` response and show watch time, participants, video list, message count, and reactions.
- [x] Share a recap summary using Android's native Sharesheet.
- [ ] Pixel-identical PNG recap-card rendering is not included in this native batch.

## Validation boundary
Source-contract checks and JavaScript unit tests run offline. Android Gradle compilation, Firebase delivery, server-backed API integration, and multi-device audio/video/invitation tests require an Android SDK, configured Firebase project, running backend/database, and physical devices or emulator. They have not been represented as passed in this migration archive.
