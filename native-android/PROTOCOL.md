# NOBAR server protocol notes

The WebSocket endpoint is the same origin as the HTTP API (`wss://nobar-d2bd.roarai.app`). On open, send `{ "t":"join", "room":"ROOMCODE", "name":"Display Name", "token":"Bearer token", "pw":"optional room password" }`.

Incoming message types handled by the current native foundation:

- `joined`: `id`, `code`, `name`, `guest`
- `members`: `host`, `list[{id,name}]`, `mode`
- `state`: `video`, `playing`, `pos`
- `chat`: `from`, `id`, `text`
- `history`: `list[{from,text}]`
- `queue`: `q`
- `proposal`: `p`, optionally `ok`
- `deny`, `err`, `wait`, `approved`, `kicked`, `replaced`

Outgoing message types currently used:

- `chat`: `{t:"chat", text}`
- `act`: `{t:"act", k:"load"|"play"|"pause"|"seek", pos, video?}`
- `vote`: `{t:"vote", ok}`
- `react`: `{t:"react", e}`

The server enforces the authoritative room rules. For rooms with three or more members only the host can directly control playback and queue. In two-person rooms, playback changes are proposals and require the other participant's vote. The server remains the source of truth for playback position; the original browser client also implements periodic drift correction that still needs native porting.

## Push registration

The existing backend's `fcm.js` module exposes authenticated HTTP endpoints:

- `POST /api/fcm/register` with `{ "token": "<FCM registration token>" }`
- `POST /api/fcm/unregister` with the same payload

Both requests require `Authorization: Bearer <NOBAR account token>`. The Android client only attempts registration when Firebase has been initialized by a supplied `google-services.json`. Guest-token support is not established by the inspected server implementation.

## Party extensions (`extras.js`)

The original server extension supports `cdstart` (host/duo start countdown), `pollstart`, `pollvote` with integer option index `i`, and `pollcancel`. Incoming events are `cd` with countdown number `n` and `poll` with options, vote counts and remaining milliseconds (`p.left`); a completed/cancelled poll arrives with `p: null` and optional result text `res`. Native UI support is present for these messages.


## Native WebRTC engine (V4)

`media/NativeCallEngine.kt` implements the NOBAR room's existing peer-signaling
envelope without changing the server protocol:

- Outbound SDP: `{t:"sig", to: peerId, data:{d:{type, sdp}}}`
- Outbound ICE: `{t:"sig", to: peerId, data:{c:{sdpMid, sdpMLineIndex, candidate}}}`
- Inbound messages use the server's `{t:"sig", from, data}` form.

The engine includes audio capture, optional Camera2 capture, peer lifecycle,
SDP offer/answer handling, ICE candidate queuing, and polite-peer collision
handling. Android UI permission prompts, remote video rendering, TURN credential
retrieval from `/api/ice`, and end-to-end device validation remain required before
this module can be treated as production-ready.


## Native WebRTC call integration (V5)

The Compose room screen now exposes a native call start/end control. It requests
Android microphone permission and requests camera permission when needed. Once
started, the app creates one `NativeCallEngine` peer connection per other room
member and routes the existing server messages through the unchanged envelope:

- outbound: `{ "t": "sig", "to": peerId, "data": { "d": { "type": "offer|answer", "sdp": "..." } } }`
- outbound ICE: `{ "t": "sig", "to": peerId, "data": { "c": { "sdpMid": "...", "sdpMLineIndex": 0, "candidate": "..." } } }`
- inbound: `{ "t": "sig", "from": peerId, "data": { ... } }`
- host moderation: inbound `{ "t": "mute" }`

Peer connections are reconciled against the room `members` list and are closed
when leaving the room or destroying the activity. Host mute disables the local
audio track. This phase sends/receives WebRTC media at the engine level, but
remote video rendering and TURN credential retrieval remain outstanding; the
room UI currently does not display remote camera tracks.


## V6 — authenticated ICE configuration

The native Android call engine requests `GET /api/ice` with `Authorization: Bearer <token>`, matching the original `public/rtc.js` behavior. Returned `iceServers` entries support `urls` as a string or array, plus optional `username` and `credential`. Public Google STUN entries remain as fallback when the endpoint is unavailable. Peer ICE and aggregate connection state are surfaced through the native call status callback.


## V7 native video rendering

The Android client now consumes remote `VideoTrack` objects from the WebRTC Unified Plan receiver callbacks and attaches them to `SurfaceViewRenderer` instances in the Compose room screen. The renderer is detached and released when the peer leaves or the call is closed. Audio continues through the WebRTC audio device module. This UI integration is source-complete but still requires Gradle compilation and physical-device/network testing.
