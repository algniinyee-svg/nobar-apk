#!/usr/bin/env python3
"""Offline source-contract checks; this does not replace Android compilation or device tests."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []
def require(condition, message):
    if not condition:
        raise SystemExit(f"FAIL: {message}")
    checks.append(message)

manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
ns = {"a": "http://schemas.android.com/apk/res/android"}
services = manifest.findall(".//service", ns)
require(any(s.get(f"{{{ns['a']}}}name") == ".push.NobarMessagingService" for s in services), "FCM service declared in manifest")
require(any(p.get(f"{{{ns['a']}}}name") == "android.permission.POST_NOTIFICATIONS" for p in manifest.findall("uses-permission", ns)), "notification permission declared")

activity = (ROOT / "app/src/main/java/com/algani/nobar/MainActivity.kt").read_text()
for event in ("pollstart", "pollvote", "pollcancel", "cdstart", "joinans", "givehost", "qadd", "qrm"):
    require(event in activity, f"native UI handles/sends {event}")

registrar = (ROOT / "app/src/main/java/com/algani/nobar/push/FcmRegistrar.kt").read_text()
for endpoint in ("/api/fcm/register", "/api/fcm/unregister"):
    require(endpoint in registrar, f"FCM registrar uses {endpoint}")

require("https://www.youtube.com/iframe_api" in activity, "YouTube IFrame player integration exists")
require("NobarNative.onTime" in activity and "youtubeSeek" in activity, "YouTube playback position and seek bridge exists")
require("Slider(" in activity and "formatMediaTime" in activity, "native synchronized seek slider exists")

service = (ROOT / "app/src/main/java/com/algani/nobar/push/NobarMessagingService.kt").read_text()
require("onNewToken" in service and "onMessageReceived" in service, "FCM token refresh and foreground message handlers exist")
print(f"NOBAR native source checks: {len(checks)} passed")
for item in checks:
    print(f"  PASS {item}")


for marker in [
    "ActivityResultContracts.RequestMultiplePermissions()",
    "NativeCallEngine(",
    '"sig" ->',
    '"mute" ->',
    "callEngine?.updateMembers(out.map",
    "callEngine?.handleSignal(from, data)",
    "callEngine?.muteByHost()",
    "closeNativeCall(); socket?.close()",
]:
    require(marker in activity, f"native call integration contains {marker}")

from pathlib import Path
rtc = Path("app/src/main/java/com/algani/nobar/media/NativeCallEngine.kt").read_text()
for marker in ["startMedia", "updateMembers", "handleSignal", "muteByHost", "createOffer", "createAnswer", "addIceCandidate"]:
    assert marker in rtc, f"Missing WebRTC engine feature: {marker}"
assert "io.github.webrtc-sdk:android:150.7871.04" in Path("app/build.gradle.kts").read_text()
print("Native WebRTC source checks: 7 passed")


# V6 authenticated ICE configuration integration checks
api = Path("app/src/main/java/com/algani/nobar/data/NobarApi.kt").read_text()
main = Path("app/src/main/java/com/algani/nobar/MainActivity.kt").read_text()
checks = {
    "native API requests authenticated /api/ice": '"/api/ice"' in api and 'header("Authorization", "Bearer $token")' in api,
    "ICE response parses iceServers array": 'optJSONArray("iceServers")' in api,
    "call engine accepts server ICE config": 'serverIceConfig: org.json.JSONArray?' in rtc,
    "TURN username and credential are applied": 'builder.setUsername(username)' in rtc and 'builder.setPassword(credential)' in rtc,
    "public STUN fallback retained": 'stun:stun.l.google.com:19302' in rtc,
    "MainActivity fetches ICE before engine creation": 'api.iceServers(token)' in main and 'serverIceConfig = ice' in main,
    "peer connection diagnostics included": 'PeerConnection.PeerConnectionState' in rtc,
}
for label, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + label)
if not all(checks.values()):
    raise SystemExit(1)
print(f"Native ICE integration checks: {len(checks)} passed")

# V7 native remote video renderer integration checks
v7_checks = {
    "WebRTC engine exposes EGL context for native renderers": "val eglContext: EglBase.Context" in rtc,
    "remote video callback is part of call engine": "onRemoteVideo: (Int, VideoTrack?) -> Unit" in rtc,
    "Unified Plan remote video receiver is consumed": "transceiver.receiver.track() as? VideoTrack" in rtc,
    "legacy remote video receiver callback is consumed": "receiver.track() as? VideoTrack" in rtc,
    "Compose room tracks remote video peers": "mutableStateMapOf<Int, VideoTrack>()" in main,
    "remote participant video tiles are rendered": "RemoteVideoTile(peerId, peerName, track)" in main,
    "Android SurfaceViewRenderer is initialized": "SurfaceViewRenderer(context)" in main and "init(eglContext, null)" in main,
    "renderer detaches track and releases resources": "track.removeSink(view)" in main and "view.release()" in main,
    "remote tiles clear on call teardown": "remoteVideoTracks.clear()" in main,
}
for label, ok in v7_checks.items():
    print(("PASS " if ok else "FAIL ") + label)
if not all(v7_checks.values()):
    raise SystemExit(1)
print(f"Native remote-video integration checks: {len(v7_checks)} passed")


# V8 native social, schedules, and invitation checks
api = Path("app/src/main/java/com/algani/nobar/data/NobarApi.kt").read_text()
v8_checks = {
    "authenticated GET API helper": "suspend fun get(path: String, token: String)" in api and 'header("Authorization", "Bearer $token")' in api,
    "authenticated DELETE API helper": "suspend fun delete(path: String, token: String)" in api,
    "native activity summary endpoint": 'api.get("/api/mystats", token)' in activity,
    "native schedule list endpoint": 'api.get("/api/sched", token)' in activity,
    "native schedule creation uses original API": 'api.post("/api/sched"' in activity and '.put("at", at)' in activity,
    "native schedule owner deletion": 'api.delete("/api/sched?id=' in activity,
    "native invitation sends original invite2 protocol": 'put("t", "invite2")' in activity and 'put("to", inviteUsername.trim())' in activity,
    "social dashboard integrated into home screen": "SocialDashboard()" in activity,
}
for label, ok in v8_checks.items():
    print(("PASS " if ok else "FAIL ") + label)
if not all(v8_checks.values()):
    raise SystemExit(1)
print(f"Native social/schedule integration checks: {len(v8_checks)} passed")


# V9–V12 native settings, wallpaper, inbox and recap checks
inbox = Path("app/src/main/java/com/algani/nobar/data/InboxSocket.kt").read_text()
v9_v12 = {
    "native theme preferences persisted": 'putString("theme", key)' in activity and 'getString("theme", "sinema")' in activity,
    "performance mode persists and suppresses remote video in lite mode": 'putString("performance", key)' in activity and 'performanceMode == "lite"' in activity,
    "wallpaper image picker integrated": 'rememberLauncherForActivityResult(ActivityResultContracts.GetContent())' in activity,
    "wallpaper upload uses original API contract": 'api.uploadJpeg("/api/wallpaper"' in activity and 'suspend fun uploadJpeg' in api,
    "wallpaper retrieval and deletion available": 'api.getBytes("/api/wallpaper"' in activity and 'api.delete("/api/wallpaper", token)' in activity,
    "inbox sends authenticated hello and visibility": 'put("t", "hello")' in inbox and 'put("t", "vis")' in inbox,
    "inbox receives original invite message": 'optString("t") == "invite"' in inbox and 'inboxInvite = invite' in activity,
    "inbox socket is closed on logout and destroy": 'inboxSocket?.close()' in activity,
    "native room requests original recap protocol": 'put("t", "recapreq")' in activity and '"recap" -> recapData' in activity,
    "native recap uses Android Sharesheet": 'Intent.createChooser' in activity and 'Intent.EXTRA_TEXT' in activity,
    "application version updated to V12": '2.0.0-native-alpha-v12' in Path("app/build.gradle.kts").read_text(),
}
for label, ok in v9_v12.items():
    print(("PASS " if ok else "FAIL ") + label)
if not all(v9_v12.values()):
    raise SystemExit(1)
print(f"Native V9–V12 integration checks: {len(v9_v12)} passed")
