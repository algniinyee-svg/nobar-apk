package com.algani.nobar

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.algani.nobar.data.NobarApi
import com.algani.nobar.data.RoomSocket
import com.algani.nobar.data.InboxSocket
import com.algani.nobar.domain.NobarJsRuntime
import com.algani.nobar.push.FcmRegistrar
import com.algani.nobar.media.NativeCallEngine
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlinx.coroutines.launch
import org.json.JSONObject

private val Bg = Color(0xFF05070C)
private val Panel = Color(0xFF111827)
private val Accent = Color(0xFF7FD6FF)
private val Muted = Color(0xFF9AA6C2)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val api by lazy { NobarApi() }
    private lateinit var js: NobarJsRuntime
    private var socket: RoomSocket? = null
    private var inboxSocket: InboxSocket? = null
    private var inboxInvite by mutableStateOf<JSONObject?>(null)
    private var inboxStatus by mutableStateOf("Inbox belum aktif")
    private var wallpaperPreview by mutableStateOf<android.graphics.Bitmap?>(null)
    private var wallpaperStatus by mutableStateOf("")
    private var themeKey by mutableStateOf("sinema")
    private var performanceMode by mutableStateOf("auto")
    private var recapData by mutableStateOf<JSONObject?>(null)
    private var callEngine: NativeCallEngine? = null
    private var callActive by mutableStateOf(false)
    private var micEnabled by mutableStateOf(false)
    private var cameraEnabled by mutableStateOf(false)
    private var cameraAvailable by mutableStateOf(false)
    private val remoteVideoTracks = mutableStateMapOf<Int, VideoTrack>()
    private var callStatus by mutableStateOf("Panggilan belum dimulai")
    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (audioGranted && isInRoom) startNativeCall(cameraGranted)
        else callStatus = "Izin mikrofon diperlukan untuk panggilan"
    }
    private var player: ExoPlayer? = null
    private var youtubeView: WebView? = null
    private var youtubeId by mutableStateOf("")
    private var youtubePositionMs by mutableLongStateOf(0L)
    private var youtubeDurationMs by mutableLongStateOf(0L)
    private var youtubePlaying by mutableStateOf(false)
    private var youtubeReady by mutableStateOf(false)
    private var token by mutableStateOf("")
    private var displayName by mutableStateOf("")
    private var roomCode by mutableStateOf("")
    private var pendingInviteRoom by mutableStateOf("")
    private var pendingInvitePassword by mutableStateOf("")
    private var roomState by mutableStateOf<JSONObject?>(null)
    private var members by mutableStateOf(listOf<Pair<Int, String>>())
    private var chat by mutableStateOf(listOf<Pair<String, String>>())
    private var status by mutableStateOf("Siap")
    private var error by mutableStateOf("")
    private var isInRoom by mutableStateOf(false)
    private var myId by mutableIntStateOf(0)
    private var hostId by mutableIntStateOf(0)
    private var roomMode by mutableStateOf("solo")
    private var queue by mutableStateOf(listOf<JSONObject>())
    private var proposal by mutableStateOf<JSONObject?>(null)
    private var joinRequests by mutableStateOf(listOf<Pair<Int, String>>())
    private var pollOptions by mutableStateOf(listOf<JSONObject>())
    private var pollCounts by mutableStateOf(listOf<Int>())
    private var pollEndsAt by mutableLongStateOf(0L)
    private var pollResult by mutableStateOf("")
    private var countdownNumber by mutableIntStateOf(0)
    private var stateReceivedAt by mutableLongStateOf(0L)
    private var currentVideo: JSONObject? = null
    private var socialStats by mutableStateOf<JSONObject?>(null)
    private var schedules by mutableStateOf(listOf<JSONObject>())
    private var socialMessage by mutableStateOf("")
    private var scheduleTitle by mutableStateOf("")
    private var scheduleRoom by mutableStateOf("")
    private var scheduleAt by mutableStateOf("")
    private var scheduleInvitees by mutableStateOf("")
    private var inviteUsername by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingInviteRoom = intent?.getStringExtra("nobar_push_room").orEmpty()
        pendingInvitePassword = intent?.getStringExtra("nobar_push_pw").orEmpty()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        js = NobarJsRuntime(this)
        player = ExoPlayer.Builder(this).build()
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val current = currentVideo
                    if (current != null && queue.isNotEmpty()) socket?.send(JSONObject().put("t", "qnext").put("cur", current))
                }
            }
        })
        token = getSharedPreferences("nobar_session", MODE_PRIVATE).getString("token", "") ?: ""
        val prefs = getSharedPreferences("nobar_preferences", MODE_PRIVATE)
        themeKey = prefs.getString("theme", "sinema") ?: "sinema"
        performanceMode = prefs.getString("performance", "auto") ?: "auto"
        setContent {
            val themeAccent = when (themeKey) {
                "fajar" -> Color(0xFFFF9A70)
                "samudra" -> Color(0xFF42C9D5)
                "hutan" -> Color(0xFF70C98A)
                "anggur" -> Color(0xFFB49AFF)
                "mono" -> Color(0xFFADB7C8)
                else -> Accent
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = themeAccent, secondary = themeAccent, background = Bg, surface = Panel)) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                if (isInRoom) RoomScreen() else LoginScreen()
                recapData?.let { RecapDialog(it) }
            }
        } }
        if (token.isNotBlank()) lifecycleScope.launch {
            runCatching { api.me(token) }.onSuccess { me ->
                displayName = me.optString("user").ifBlank { me.optString("guest") }
                if (displayName.isBlank()) updateToken("")
                else {
                    runCatching { FcmRegistrar.register(this@MainActivity, token) }
                    startInbox()
                    refreshSocial()
                    loadWallpaper()
                }
            }.onFailure { status = "Sesi tersimpan; server belum terjangkau" }
        }
    }

    private fun updateToken(value: String) {
        val previous = token
        token = value
        getSharedPreferences("nobar_session", MODE_PRIVATE).edit().putString("token", value).apply()
        lifecycleScope.launch {
            if (value.isBlank() && previous.isNotBlank()) {
                runCatching { FcmRegistrar.unregister(this@MainActivity, previous) }
            } else if (value.isNotBlank()) {
                runCatching { FcmRegistrar.register(this@MainActivity, value) }
                startInbox()
                refreshSocial()
                loadWallpaper()
            }
            if (value.isBlank()) { inboxSocket?.close(); inboxSocket = null; inboxInvite = null; wallpaperPreview = null }
        }
    }

    @Composable private fun LoginScreen() {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var guestName by remember { mutableStateOf("") }
        var room by remember { mutableStateOf(pendingInviteRoom) }
        var roomPassword by remember { mutableStateOf(pendingInvitePassword) }
        LaunchedEffect(pendingInviteRoom, pendingInvitePassword) {
            if (pendingInviteRoom.isNotBlank()) room = pendingInviteRoom
            if (pendingInvitePassword.isNotBlank()) roomPassword = pendingInvitePassword
        }
        var register by remember { mutableStateOf(false) }
        var showGuest by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("▶", color = Accent, fontSize = 54.sp)
            Text("Nobar", color = Color.White, fontSize = 42.sp)
            Text("Nonton bareng, ngobrol bareng.", color = Muted)
            if (token.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Personalisasi", color = Color.White)
                        Text("Tema warna", color = Muted, fontSize = 11.sp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("sinema" to "Sinema", "fajar" to "Fajar", "samudra" to "Samudra", "hutan" to "Hutan", "anggur" to "Anggur", "mono" to "Mono").forEach { (key, label) ->
                                FilterChip(selected = themeKey == key, onClick = { themeKey = key; getSharedPreferences("nobar_preferences", MODE_PRIVATE).edit().putString("theme", key).apply() }, label = { Text(label, fontSize = 10.sp) })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Performa", color = Muted, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            listOf("auto" to "Auto", "lite" to "Hemat", "full" to "Penuh").forEach { (key, label) ->
                                TextButton(onClick = { performanceMode = key; getSharedPreferences("nobar_preferences", MODE_PRIVATE).edit().putString("performance", key).apply() }) { Text(if (performanceMode == key) "✓ $label" else label, fontSize = 10.sp) }
                            }
                        }
                        Text("Wallpaper akun", color = Muted, fontSize = 11.sp)
                        wallpaperPreview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(100.dp), contentScale = ContentScale.Crop) }
                        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            if (uri != null) lifecycleScope.launch {
                                wallpaperStatus = "Memproses gambar…"
                                runCatching {
                                    val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("File tidak bisa dibaca")
                                    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: error("Format gambar tidak didukung")
                                    val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
                                    val resized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
                                    var bytes = byteArrayOf()
                                    for (quality in listOf(78, 65, 52, 40, 30)) {
                                        val out = java.io.ByteArrayOutputStream()
                                        resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                                        bytes = out.toByteArray()
                                        if (bytes.size <= 780 * 1024) break
                                    }
                                    require(bytes.size in 1000..(800 * 1024)) { "Foto tidak dapat diperkecil di bawah 800 KB" }
                                    api.uploadJpeg("/api/wallpaper", bytes, token)
                                    wallpaperPreview = resized
                                }.onSuccess { wallpaperStatus = "Wallpaper tersimpan di akun." }.onFailure { wallpaperStatus = it.message ?: "Gagal mengunggah" }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { imagePicker.launch("image/*") }) { Text("Pilih foto") }
                            TextButton(onClick = { lifecycleScope.launch { runCatching { api.delete("/api/wallpaper", token) }.onSuccess { wallpaperPreview = null; wallpaperStatus = "Wallpaper dihapus." }.onFailure { wallpaperStatus = it.message ?: "Gagal menghapus" } } }) { Text("Hapus") }
                        }
                        if (wallpaperStatus.isNotBlank()) Text(wallpaperStatus, color = Accent, fontSize = 11.sp)
                        Text(inboxStatus, color = Muted, fontSize = 10.sp)
                    }
                }
                inboxInvite?.let { invite ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📨 Undangan Nobar", color = Accent)
                            Text("${invite.optString("from", "Teman")} mengajakmu ke room ${invite.optString("room")}", color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { pendingInviteRoom = invite.optString("room"); pendingInvitePassword = invite.optString("pw"); inboxInvite = null }) { Text("Isi kode room") }
                                TextButton(onClick = { inboxInvite = null }) { Text("Abaikan") }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (token.isBlank()) {
                        if (showGuest) {
                            Field(guestName, { guestName = it }, "Nama panggilan")
                            Button(onClick = { lifecycleScope.launch { runCatching { api.post("/api/guest", JSONObject().put("name", guestName)).also { setToken(it.optString("token")); displayName = it.optString("name") } }.onFailure { error = it.message ?: "Gagal masuk sebagai tamu" } } }, modifier = Modifier.fillMaxWidth()) { Text("Lanjut sebagai tamu") }
                            TextButton(onClick = { showGuest = false }) { Text("Kembali ke login") }
                        } else {
                            Text(if (register) "Buat akun" else "Masuk", color = Color.White, fontSize = 20.sp)
                            Field(username, { username = it }, "Username")
                            OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                            Button(onClick = { lifecycleScope.launch { runCatching { api.post(if (register) "/api/register" else "/api/login", JSONObject().put("username", username).put("password", password)).also { setToken(it.optString("token")); displayName = it.optString("user"); error = if (it.has("recovery")) "Simpan kode pemulihan: ${it.optString("recovery")}" else "" } }.onFailure { error = it.message ?: "Login gagal" } } }, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Daftar" else "Masuk") }
                            TextButton(onClick = { register = !register }) { Text(if (register) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar") }
                            TextButton(onClick = { showGuest = true }) { Text("Masuk sebagai tamu") }
                        }
                    } else {
                        Text("Halo, $displayName", color = Color.White, fontSize = 20.sp)
                        Field(room, { room = it.uppercase().take(12); pendingInviteRoom = "" }, "Kode room")
                        OutlinedTextField(roomPassword, { roomPassword = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password room (opsional)") }, singleLine = true)
                        Button(onClick = { joinRoom(room, roomPassword) }, modifier = Modifier.fillMaxWidth(), enabled = room.isNotBlank()) { Text("Masuk room") }
                        TextButton(onClick = { setToken(""); displayName = "" }) { Text("Ganti akun") }
                    }
                    if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
            }
            if (token.isNotBlank()) SocialDashboard()
            Spacer(Modifier.height(14.dp))
            Text("Sinkron · Chat · Voice · Video · Maks. 8 orang", color = Muted, fontSize = 12.sp)
            Text("$status", color = Muted, fontSize = 11.sp)
        }
    }

    @Composable private fun RoomScreen() {
        var videoUrl by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }
        var pollClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(isInRoom) {
            while (isInRoom) { delay(1000); pollClock = System.currentTimeMillis() }
        }
        LaunchedEffect(isInRoom) {
            while (isInRoom) {
                delay(3000)
                val state = roomState
                if (state != null && state.optBoolean("playing") && stateReceivedAt > 0) {
                    val expected = ((state.optDouble("pos", 0.0) + (System.currentTimeMillis() - stateReceivedAt) / 1000.0) * 1000).toLong()
                    val type = state.optJSONObject("video")?.optString("type")
                    if (type == "url") {
                        val actual = player?.currentPosition ?: 0L
                        if (kotlin.math.abs(actual - expected) > 3500) player?.seekTo(expected)
                    } else if (type == "yt" && youtubeReady) {
                        if (kotlin.math.abs(youtubePositionMs - expected) > 4000) youtubeSeek(expected / 1000.0)
                        if (!youtubePlaying) youtubePlay()
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("ROOM $roomCode", color = Color.White, fontSize = 18.sp); Text("${members.size}/8 anggota · $status", color = Muted, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { socket?.send(JSONObject().put("t", "recapreq")) }) { Text("Rekap") }
                    OutlinedButton(onClick = { leaveRoom() }) { Text("Keluar") }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (callActive) closeNativeCall() else {
                                val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                    needed += Manifest.permission.CAMERA
                                }
                                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || needed.size > 1) {
                                    callPermission.launch(needed.toTypedArray())
                                } else startNativeCall(ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                            }
                        }) { Text(if (callActive) "Akhiri panggilan" else "Mulai panggilan") }
                        OutlinedButton(onClick = {
                            micEnabled = callEngine?.toggleMicrophone() ?: false
                        }, enabled = callActive) { Text(if (micEnabled) "Mikrofon ON" else "Mikrofon OFF") }
                        OutlinedButton(onClick = {
                            cameraEnabled = callEngine?.toggleCamera() ?: false
                        }, enabled = callActive && cameraAvailable) { Text(if (cameraEnabled) "Kamera ON" else "Kamera OFF") }
                    }
                    Text(callStatus, color = Muted, fontSize = 11.sp)
                    if (remoteVideoTracks.isNotEmpty() && performanceMode == "lite") {
                        Text("Mode hemat aktif · ${remoteVideoTracks.size} video peserta disembunyikan; audio tetap berjalan", color = Muted, fontSize = 11.sp)
                    } else if (remoteVideoTracks.isNotEmpty()) {
                        Text("Video peserta", color = Accent, fontSize = 13.sp)
                        remoteVideoTracks.toMap().forEach { (peerId, track) ->
                            val peerName = members.firstOrNull { it.first == peerId }?.second ?: "Peserta #$peerId"
                            RemoteVideoTile(peerId, peerName, track)
                        }
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black), modifier = Modifier.fillMaxWidth().height(210.dp)) {
                if (youtubeId.isNotBlank()) {
                    AndroidView(factory = { ctx -> createYoutubeView(ctx, youtubeId) }, update = { view ->
                        if (view.tag != youtubeId) { view.tag = youtubeId; loadYoutubeHtml(view, youtubeId) }
                    }, modifier = Modifier.fillMaxSize())
                } else {
                    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = this@MainActivity.player; useController = true } }, modifier = Modifier.fillMaxSize())
                }
            }
            if (youtubeId.isNotBlank()) {
                Text(if (youtubeReady) "YouTube · ${if (youtubePlaying) "Diputar" else "Jeda"}" else "Memuat pemutar YouTube…", color = Muted, fontSize = 11.sp)
            }
            val durationMs = if (youtubeId.isNotBlank()) youtubeDurationMs else (player?.duration ?: 0L).coerceAtLeast(0L)
            val positionMs = if (youtubeId.isNotBlank()) youtubePositionMs else (player?.currentPosition ?: 0L).coerceAtLeast(0L)
            var seekDraft by remember { mutableFloatStateOf(0f) }
            var isSeeking by remember { mutableStateOf(false) }
            if (durationMs > 0L) {
                Slider(
                    value = if (isSeeking) seekDraft else positionMs.coerceAtMost(durationMs).toFloat(),
                    onValueChange = { seekDraft = it; isSeeking = true },
                    onValueChangeFinished = {
                        val seconds = seekDraft.toDouble() / 1000.0
                        sendAct("seek", seconds)
                        if (youtubeId.isNotBlank()) youtubeSeek(seconds) else player?.seekTo(seekDraft.toLong())
                        isSeeking = false
                    },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${formatMediaTime(positionMs)} / ${formatMediaTime(durationMs)}", color = Muted, fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(videoUrl, { videoUrl = it }, modifier = Modifier.weight(1f), label = { Text("Link video MP4 / URL") }, singleLine = true)
                Spacer(Modifier.width(6.dp))
                Column {
                    Button(onClick = { loadVideo(videoUrl) }) { Text("Putar") }
                    TextButton(onClick = { addToQueue(videoUrl) }) { Text("+ Antrean") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val playing = if (youtubeId.isNotBlank()) youtubePlaying else player?.isPlaying == true
                    sendAct(if (playing) "pause" else "play", if (youtubeId.isNotBlank()) youtubePositionMs / 1000.0 else player?.currentPosition?.div(1000.0) ?: 0.0)
                    if (youtubeId.isNotBlank()) { if (playing) youtubePause() else youtubePlay() }
                }) { Text(if (if (youtubeId.isNotBlank()) youtubePlaying else player?.isPlaying == true) "Jeda" else "Play") }
                OutlinedButton(onClick = { sendAct("seek", if (youtubeId.isNotBlank()) youtubePositionMs / 1000.0 else player?.currentPosition?.div(1000.0) ?: 0.0) }) { Text("Sinkronkan") }
                OutlinedButton(onClick = { socket?.send(JSONObject().put("t", "react").put("e", "❤️")) }) { Text("❤️") }
                OutlinedButton(onClick = { socket?.send(JSONObject().put("t", "cdstart")) }, enabled = countdownNumber <= 0 && roomState?.optJSONObject("video") != null) { Text("⏱ Bareng") }
            }
            if (countdownNumber > 0) {
                Text("Mulai bersama dalam $countdownNumber…", color = Accent, fontSize = 20.sp)
            } else if (countdownNumber == 0 && status == "Mulai bersama!") {
                Text("▶ Mulai bersama!", color = Accent)
            }
            Text("Anggota", color = Accent)
            members.forEach { (id, memberName) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(memberName + if (id == hostId) " · Host" else "", color = Muted, modifier = Modifier.weight(1f), fontSize = 12.sp)
                    if (myId == hostId && id != myId) {
                        TextButton(onClick = { socket?.send(JSONObject().put("t", "givehost").put("to", id)) }) { Text("Jadikan host") }
                        TextButton(onClick = { socket?.send(JSONObject().put("t", "kick").put("to", id)) }) { Text("Keluarkan") }
                        TextButton(onClick = { socket?.send(JSONObject().put("t", "mute").put("to", id)) }) { Text("Mute") }
                    }
                }
            }
            if (joinRequests.isNotEmpty() && myId == hostId) {
                Text("Permintaan masuk", color = Accent)
                joinRequests.forEach { (id, who) -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(who, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = { socket?.send(JSONObject().put("t", "joinans").put("id", id).put("ok", true)); joinRequests = joinRequests.filterNot { it.first == id } }) { Text("Setuju") }
                    TextButton(onClick = { socket?.send(JSONObject().put("t", "joinans").put("id", id).put("ok", false)); joinRequests = joinRequests.filterNot { it.first == id } }) { Text("Tolak") }
                } }
            }
            if (queue.isNotEmpty()) {
                Text("Antrean video", color = Accent)
                val canManageQueue = members.size < 3 || myId == hostId
                queue.forEachIndexed { index, item -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (item.optString("type") == "yt") "YouTube: ${item.optString("id")}" else item.optString("url"), color = Muted, modifier = Modifier.weight(1f), fontSize = 11.sp)
                    TextButton(enabled = canManageQueue, onClick = { socket?.send(JSONObject().put("t", "qrm").put("i", index)) }) { Text("Hapus") }
                } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { socket?.send(JSONObject().put("t", "pollstart")); pollResult = "" }, enabled = members.size >= 2 && queue.size >= 2 && pollOptions.isEmpty()) { Text("🗳 Voting film") }
                if (pollOptions.isNotEmpty() && (members.size < 3 || myId == hostId)) {
                    TextButton(onClick = { socket?.send(JSONObject().put("t", "pollcancel")) }) { Text("Batalkan voting") }
                }
            }
            if (pollOptions.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🗳 Voting film · ${((pollEndsAt - pollClock).coerceAtLeast(0L) / 1000L)} dtk", color = Accent)
                        pollOptions.forEachIndexed { index, option ->
                            val label = if (option.optString("type") == "yt") "YouTube: ${option.optString("id")}" else option.optString("url")
                            OutlinedButton(onClick = { socket?.send(JSONObject().put("t", "pollvote").put("i", index)) }, modifier = Modifier.fillMaxWidth()) {
                                Text("${index + 1}. $label · ${pollCounts.getOrElse(index) { 0 }} suara")
                            }
                        }
                    }
                }
            }
            if (pollResult.isNotBlank()) Text(pollResult, color = Accent, fontSize = 12.sp)
            proposal?.let { p -> Card(colors = CardDefaults.cardColors(containerColor = Panel)) { Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("${p.optString("name")} mengusulkan ${p.optString("label")}", color = Color.White, modifier = Modifier.weight(1f)); TextButton(onClick = { socket?.send(JSONObject().put("t", "vote").put("ok", true)) }) { Text("Setuju") }; TextButton(onClick = { socket?.send(JSONObject().put("t", "vote").put("ok", false)) }) { Text("Tolak") } } } }
            if (token.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(inviteUsername, { inviteUsername = it.take(16) }, modifier = Modifier.weight(1f), label = { Text("Undang username") }, singleLine = true)
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = { socket?.send(JSONObject().put("t", "invite2").put("to", inviteUsername.trim())); inviteUsername = "" }) { Text("Undang") }
                    }
                }
            }
            Text("Chat", color = Accent)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(10.dp)) {
                items(chat) { (from, text) -> Text("$from: $text", color = Color.White, modifier = Modifier.padding(vertical = 3.dp), fontSize = 13.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(message, { message = it }, modifier = Modifier.weight(1f), label = { Text("Ketik pesan…") }, singleLine = true)
                Spacer(Modifier.width(6.dp))
                Button(onClick = { val clean = js.call("validateChat", message).toString(); if (clean.isNotBlank()) socket?.send(JSONObject().put("t", "chat").put("text", clean)); message = "" }) { Text("Kirim") }
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80), fontSize = 12.sp)
        }
    }

    @Composable private fun RecapDialog(data: JSONObject) {
        val users = data.optJSONArray("users")
        val videos = data.optJSONArray("videos")
        val msgs = data.optJSONObject("msgs")
        val reacts = data.optJSONObject("reacts")
        val messageTotal = (0 until (msgs?.length() ?: 0)).sumOf { msgs?.optInt(msgs.names()?.optString(it) ?: "", 0) ?: 0 }
        val reactionTotal = (0 until (reacts?.length() ?: 0)).sumOf { reacts?.optInt(reacts.names()?.optString(it) ?: "", 0) ?: 0 }
        val userNames = buildList { if (users != null) for (i in 0 until users.length()) add(users.optString(i)) }
        val videoNames = buildList { if (videos != null) for (i in 0 until videos.length()) { val v = videos.optJSONObject(i) ?: continue; add(if (v.optString("type") == "yt") "YouTube · ${v.optString("id")}" else v.optString("url")) } }
        AlertDialog(onDismissRequest = { recapData = null }, title = { Text("Rekap Nobar · ${data.optString("code")}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Waktu nonton: ${data.optLong("watch") / 60} menit")
                Text("Peserta: ${userNames.size} · Pesan: $messageTotal · Reaksi: $reactionTotal")
                Text("Video diputar: ${videoNames.size}")
                videoNames.take(5).forEachIndexed { i, name -> Text("${i + 1}. $name", fontSize = 11.sp, color = Muted) }
                if (userNames.isNotEmpty()) Text("Bersama: ${userNames.joinToString(", ")}", fontSize = 11.sp, color = Muted)
            } },
            confirmButton = { TextButton(onClick = {
                val share = "🎬 Rekap Nobar ${data.optString("code")}\nWaktu nonton: ${data.optLong("watch") / 60} menit\nPeserta: ${userNames.joinToString(", ")}\nPesan: $messageTotal · Reaksi: $reactionTotal\nVideo: ${videoNames.joinToString("; ")}"
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, share), "Bagikan rekap Nobar"))
                recapData = null
            }) { Text("Bagikan") } },
            dismissButton = { TextButton(onClick = { recapData = null }) { Text("Tutup") } })
    }

    private fun startInbox() {
        if (token.isBlank()) return
        if (inboxSocket == null) inboxSocket = InboxSocket(
            onInvite = { invite -> runOnUiThread { inboxInvite = invite } },
            onStatus = { message -> runOnUiThread { inboxStatus = message } }
        )
        inboxSocket?.connect(token)
    }

    private fun loadWallpaper() {
        if (token.isBlank()) return
        lifecycleScope.launch {
            runCatching { api.getBytes("/api/wallpaper", token) }.onSuccess { bytes ->
                wallpaperPreview = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
    }

    private fun refreshSocial() {
        if (token.isBlank()) return
        lifecycleScope.launch {
            runCatching { api.get("/api/mystats", token) }.onSuccess { socialStats = it }
                .onFailure { socialMessage = it.message ?: "Gagal memuat statistik" }
            runCatching { api.get("/api/sched", token) }.onSuccess { result ->
                val arr = result.optJSONArray("items")
                schedules = buildList { if (arr != null) for (i in 0 until arr.length()) add(arr.getJSONObject(i)) }
            }.onFailure { socialMessage = it.message ?: "Gagal memuat jadwal" }
        }
    }

    private fun createSchedule() {
        val at = scheduleAt.trim().toLongOrNull()
        if (scheduleTitle.isBlank() || scheduleRoom.isBlank() || at == null) {
            socialMessage = "Isi judul, kode room, dan waktu mulai dalam Unix milidetik"; return
        }
        val invites = scheduleInvitees.split(",").map { it.trim() }.filter { it.isNotBlank() }
        lifecycleScope.launch {
            runCatching { api.post("/api/sched", JSONObject().put("title", scheduleTitle).put("room", scheduleRoom.uppercase()).put("at", at).put("invitees", org.json.JSONArray(invites)), token) }
                .onSuccess { result -> socialMessage = if ((result.optJSONArray("unknown")?.length() ?: 0) > 0) "Jadwal dibuat; beberapa username tidak ditemukan" else "Jadwal berhasil dibuat"; scheduleTitle = ""; scheduleAt = ""; scheduleInvitees = ""; refreshSocial() }
                .onFailure { socialMessage = it.message ?: "Gagal membuat jadwal" }
        }
    }

    @Composable private fun SocialDashboard() {
        LaunchedEffect(token) { if (token.isNotBlank()) refreshSocial() }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Profil & aktivitas", color = Accent, fontSize = 16.sp)
                    TextButton(onClick = { refreshSocial() }) { Text("Muat ulang") }
                }
                val stats = socialStats
                if (stats != null) {
                    Text("${stats.optInt("minutes")} menit ditonton · ${stats.optInt("messages")} pesan · ${stats.optInt("reactions")} reaksi", color = Color.White, fontSize = 12.sp)
                    Text("${stats.optInt("joined")} room diikuti · ${stats.optInt("created")} dibuat · streak ${stats.optInt("streak")} hari", color = Muted, fontSize = 11.sp)
                    val badges = stats.optJSONArray("badges")
                    if (badges != null) {
                        val earned = buildList { for (i in 0 until badges.length()) { val b = badges.getJSONObject(i); if (b.optBoolean("got")) add("${b.optString("icon")} ${b.optString("name")}") } }
                        if (earned.isNotEmpty()) Text("Badge: ${earned.joinToString(" · ")}", color = Accent, fontSize = 11.sp)
                    }
                } else Text("Statistik belum tersedia", color = Muted, fontSize = 11.sp)
                Divider(color = Muted.copy(alpha = .25f))
                Text("Jadwalkan nobar", color = Color.White)
                Field(scheduleTitle, { scheduleTitle = it.take(40) }, "Judul acara")
                Field(scheduleRoom, { scheduleRoom = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(12) }, "Kode room")
                Field(scheduleAt, { scheduleAt = it.filter(Char::isDigit) }, "Waktu mulai · Unix milidetik")
                Field(scheduleInvitees, { scheduleInvitees = it.take(200) }, "Undang username, pisahkan koma (opsional)")
                Button(onClick = { createSchedule() }, modifier = Modifier.fillMaxWidth()) { Text("Buat jadwal") }
                Text("Jadwal mendatang", color = Accent)
                if (schedules.isEmpty()) Text("Belum ada jadwal.", color = Muted, fontSize = 11.sp)
                schedules.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.optString("title"), color = Color.White, fontSize = 12.sp)
                            Text("Room ${item.optString("room")} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.optLong("ts"))) }", color = Muted, fontSize = 10.sp)
                        }
                        if (item.optBoolean("mine")) TextButton(onClick = { lifecycleScope.launch { runCatching { api.delete("/api/sched?id=${item.optLong("id")}", token) }.onSuccess { refreshSocial() }.onFailure { socialMessage = it.message ?: "Gagal menghapus" } } }) { Text("Hapus") }
                    }
                }
                if (socialMessage.isNotBlank()) Text(socialMessage, color = Accent, fontSize = 11.sp)
            }
        }
    }

    @Composable private fun Field(value: String, onChange: (String) -> Unit, label: String) {
        OutlinedTextField(value, onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
    }

    private fun joinRoom(raw: String, password: String) {
        val code = js.call("normalizeRoom", raw).toString()
        if (code.isBlank()) { error = "Kode room tidak valid"; return }
        closeNativeCall()
        roomCode = code; error = ""; chat = emptyList(); members = emptyList(); pollOptions = emptyList(); pollCounts = emptyList(); pollResult = ""; countdownNumber = 0; isInRoom = true
        val ws = RoomSocket(onMessage = { m -> runOnUiThread { handleRoomMessage(m) } }, onStatus = { s -> runOnUiThread { status = s } })
        socket = ws
        ws.connect(code, displayName, token, password)
    }

    private fun handleRoomMessage(m: JSONObject) {
        when (m.optString("t")) {
            "joined" -> { myId = m.optInt("id"); displayName = m.optString("name", displayName); status = "Bergabung" }
            "members" -> {
                hostId = m.optInt("host"); roomMode = m.optString("mode", "solo")
                val list = m.optJSONArray("list"); val out = mutableListOf<Pair<Int, String>>()
                if (list != null) for (i in 0 until list.length()) { val x = list.getJSONObject(i); out += x.optInt("id") to x.optString("name") }
                members = out
                callEngine?.updateMembers(out.map { it.first })
            }
            "sig" -> {
                val from = m.optInt("from")
                val data = m.optJSONObject("data")
                if (from > 0 && data != null) callEngine?.handleSignal(from, data)
            }
            "mute" -> {
                callEngine?.muteByHost()
                micEnabled = false
                callStatus = "Mikrofon dibisukan oleh host"
            }
            "state" -> {
                roomState = m
                stateReceivedAt = System.currentTimeMillis()
                val video = m.optJSONObject("video")
                currentVideo = video
                if (video != null && video.optString("type") == "yt") {
                    val id = video.optString("id")
                    if (youtubeId != id) {
                        youtubeId = id
                        youtubeReady = false
                        youtubePlaying = false
                        player?.stop()
                    }
                    val target = (m.optDouble("pos", 0.0) * 1000).toLong()
                    if (youtubeReady && kotlin.math.abs(youtubePositionMs - target) > 2500) youtubeSeek(target / 1000.0)
                    if (m.optBoolean("playing")) youtubePlay() else youtubePause()
                    error = ""
                } else if (video != null && video.optString("type") == "url") {
                    youtubeId = ""
                    youtubeReady = false
                    val url = video.optString("url")
                    if (player?.currentMediaItem?.localConfiguration?.uri?.toString() != url) {
                        player?.setMediaItem(MediaItem.fromUri(url)); player?.prepare()
                    }
                    val target = (m.optDouble("pos", 0.0) * 1000).toLong()
                    if (kotlin.math.abs((player?.currentPosition ?: 0L) - target) > 2500) player?.seekTo(target)
                    if (m.optBoolean("playing")) player?.play() else player?.pause()
                } else if (video == null) {
                    youtubeId = ""
                    youtubeReady = false
                    player?.stop()
                }
            }
            "info" -> status = m.optString("msg", status)
            "recap" -> recapData = m.optJSONObject("data")
            "chat" -> chat = (chat + (m.optString("from") to m.optString("text"))).takeLast(200)
            "history" -> {
                val list = m.optJSONArray("list"); val out = mutableListOf<Pair<String, String>>()
                if (list != null) for (i in 0 until list.length()) { val x = list.getJSONObject(i); out += x.optString("from") to x.optString("text") }
                chat = out.takeLast(200)
            }
            "queue" -> { val q = m.optJSONArray("q"); val out = mutableListOf<JSONObject>(); if (q != null) for (i in 0 until q.length()) out += q.getJSONObject(i); queue = out }
            "proposal" -> proposal = m.optJSONObject("p")
            "joinreq" -> joinRequests = (joinRequests + (m.optInt("id") to m.optString("name"))).distinctBy { it.first }
            "joinreq_clear" -> joinRequests = emptyList()
            "poll" -> {
                val p = m.optJSONObject("p")
                if (p == null) {
                    pollOptions = emptyList(); pollCounts = emptyList(); pollEndsAt = 0L
                    pollResult = m.optString("res")
                } else {
                    val opts = p.optJSONArray("opts"); val counts = p.optJSONArray("counts")
                    pollOptions = buildList { if (opts != null) for (i in 0 until opts.length()) add(opts.getJSONObject(i)) }
                    pollCounts = buildList { if (counts != null) for (i in 0 until counts.length()) add(counts.optInt(i)) }
                    pollEndsAt = System.currentTimeMillis() + p.optLong("left", 0L)
                    pollResult = ""
                }
            }
            "cd" -> {
                countdownNumber = m.optInt("n", 0)
                if (countdownNumber == 0) status = "Mulai bersama!"
            }
            "deny", "err" -> error = m.optString("msg", "Permintaan ditolak")
            "wait" -> status = "Menunggu persetujuan host"
            "approved" -> status = "Disetujui, menyambungkan kembali"
            "kicked" -> { error = "Kamu dikeluarkan oleh host"; leaveRoom() }
            "replaced" -> { error = "Akun digunakan di perangkat lain"; leaveRoom() }
        }
    }

    private fun startNativeCall(withCamera: Boolean) {
        if (!isInRoom || myId <= 0) {
            callStatus = "Gabung ke room terlebih dahulu"
            return
        }
        callStatus = "Mengambil konfigurasi ICE…"
        lifecycleScope.launch {
            runCatching {
                val ice = runCatching { api.iceServers(token) }.getOrNull()
                callEngine?.close()
                callEngine = NativeCallEngine(
                    context = this@MainActivity,
                    myId = myId,
                    sendSignal = { peerId, data ->
                        socket?.send(JSONObject().put("t", "sig").put("to", peerId).put("data", data))
                    },
                    onStatus = { message -> runOnUiThread { callStatus = message } },
                    onRemoteVideo = { peerId, track -> runOnUiThread { if (track == null) remoteVideoTracks.remove(peerId) else remoteVideoTracks[peerId] = track } },
                    serverIceConfig = ice
                )
                callEngine?.startMedia(withCamera)
                callEngine?.updateMembers(members.map { it.first })
                callActive = true
                micEnabled = callEngine?.microphoneEnabled == true
                cameraEnabled = callEngine?.cameraEnabled == true
                cameraAvailable = callEngine?.localVideoTrack != null
                callStatus = if (ice != null && ice.length() > 0) "ICE server NOBAR dimuat; panggilan aktif" else "STUN fallback aktif; panggilan aktif"
            }.onFailure {
                closeNativeCall()
                callStatus = "Panggilan gagal dimulai: ${it.message ?: "kesalahan perangkat"}"
            }
        }
    }

    private fun closeNativeCall() {
        runCatching { callEngine?.close() }
        remoteVideoTracks.clear()
        callEngine = null
        callActive = false
        micEnabled = false
        cameraEnabled = false
        cameraAvailable = false
        callStatus = "Panggilan belum dimulai"
    }

    @Composable
    private fun RemoteVideoTile(peerId: Int, peerName: String, track: VideoTrack) {
        var renderer by remember(peerId, track) { mutableStateOf<SurfaceViewRenderer?>(null) }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(peerName, color = Color.White, fontSize = 12.sp)
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    factory = { context ->
                        val eglContext = callEngine?.eglContext ?: throw IllegalStateException("Mesin panggilan belum siap")
                        SurfaceViewRenderer(context).apply {
                            init(eglContext, null)
                            setEnableHardwareScaler(true)
                            setMirror(false)
                            renderer = this
                            track.addSink(this)
                        }
                    },
                    update = { view ->
                        if (renderer !== view) {
                            renderer = view
                            track.addSink(view)
                        }
                    }
                )
            }
        }
        DisposableEffect(track, renderer) {
            onDispose {
                renderer?.let { view ->
                    runCatching { track.removeSink(view) }
                    runCatching { view.release() }
                }
                renderer = null
            }
        }
    }

    private fun createYoutubeView(context: android.content.Context, id: String): WebView {
        return WebView(context).apply {
            tag = id
            setBackgroundColor(android.graphics.Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    return !(url.startsWith("https://www.youtube.com/") || url.startsWith("https://youtube.com/") || url.startsWith("https://nobar-d2bd.roarai.app/"))
                }
            }
            addJavascriptInterface(YouTubeBridge(), "NobarNative")
            loadYoutubeHtml(this, id)
            youtubeView = this
        }
    }

    private fun loadYoutubeHtml(view: WebView, id: String) {
        val safeId = id.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.take(11)
        if (safeId.length != 11) { error = "ID YouTube tidak valid"; return }
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"><style>html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden}iframe{width:100%;height:100%;border:0}</style></head><body><div id="player"></div><script src="https://www.youtube.com/iframe_api"></script><script>let p;function onYouTubeIframeAPIReady(){p=new YT.Player('player',{videoId:'$safeId',playerVars:{playsinline:1,controls:1,rel:0,modestbranding:1},events:{onReady:()=>NobarNative.onReady(),onStateChange:e=>NobarNative.onState(e.data),onError:e=>NobarNative.onError(e.data)}})}function playNobar(){if(p)p.playVideo()}function pauseNobar(){if(p)p.pauseVideo()}function seekNobar(s){if(p)p.seekTo(s,true)}setInterval(()=>{if(p&&p.getCurrentTime)NobarNative.onTime(p.getCurrentTime(),p.getDuration()||0)},1000);</script></body></html>"""
        view.loadDataWithBaseURL("https://nobar-d2bd.roarai.app/", html, "text/html", "UTF-8", null)
    }

    private inner class YouTubeBridge {
        @JavascriptInterface fun onReady() {
            runOnUiThread {
                youtubeReady = true
                error = ""
                val state = roomState
                val video = state?.optJSONObject("video")
                if (video?.optString("type") == "yt" && video.optString("id") == youtubeId) {
                    youtubeSeek(state.optDouble("pos", 0.0))
                    if (state.optBoolean("playing")) youtubePlay() else youtubePause()
                }
            }
        }
        @JavascriptInterface fun onState(state: Int) { runOnUiThread { youtubePlaying = state == 1 } }
        @JavascriptInterface fun onTime(position: Double, duration: Double) { runOnUiThread { youtubePositionMs = (position * 1000).toLong(); youtubeDurationMs = (duration * 1000).toLong() } }
        @JavascriptInterface fun onError(code: Int) { runOnUiThread { error = "YouTube tidak dapat memutar video (kode $code)" } }
    }

    private fun youtubePlay() { youtubeView?.evaluateJavascript("playNobar()", null) }
    private fun youtubePause() { youtubeView?.evaluateJavascript("pauseNobar()", null) }
    private fun youtubeSeek(seconds: Double) { youtubeView?.evaluateJavascript("seekNobar(${seconds.coerceAtLeast(0.0)})", null) }

    private fun formatMediaTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    private fun parseVideo(input: String): JSONObject? {
        val value = input.trim()
        val yt = Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/))([\\w-]{11})").find(value)
        return if (yt != null) JSONObject().put("type", "yt").put("id", yt.groupValues[1]) else if (value.startsWith("https://") || value.startsWith("http://")) JSONObject().put("type", "url").put("url", value) else null
    }
    private fun loadVideo(input: String) {
        val video = parseVideo(input)
        if (video == null) { error = "Link video tidak valid"; return }
        if (members.size >= 3 && myId != hostId) { error = "Hanya host yang bisa mengontrol"; return }
        socket?.send(JSONObject().put("t", "act").put("k", "load").put("video", video))
    }
    private fun addToQueue(input: String) {
        val video = parseVideo(input)
        if (video == null) { error = "Link video tidak valid"; return }
        if (queue.size >= 20) { error = "Antrean penuh"; return }
        socket?.send(JSONObject().put("t", "qadd").put("video", video))
    }
    private fun sendAct(action: String, position: Double = player?.currentPosition?.div(1000.0) ?: 0.0) {
        if (members.size >= 3 && myId != hostId) { error = "Hanya host yang bisa mengontrol"; return }
        socket?.send(JSONObject().put("t", "act").put("k", action).put("pos", position))
    }
    private fun leaveRoom() { closeNativeCall(); socket?.close(); socket = null; player?.stop(); youtubePause(); youtubeId = ""; youtubeReady = false; youtubePlaying = false; isInRoom = false; pollOptions = emptyList(); pollCounts = emptyList(); countdownNumber = 0; status = "Siap" }
    override fun onResume() { super.onResume(); inboxSocket?.visibility(false) }
    override fun onPause() { inboxSocket?.visibility(true); super.onPause() }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingInviteRoom = intent.getStringExtra("nobar_push_room").orEmpty()
        pendingInvitePassword = intent.getStringExtra("nobar_push_pw").orEmpty()
    }

    override fun onDestroy() { closeNativeCall(); socket?.close(); inboxSocket?.close(); youtubeView?.apply { stopLoading(); removeJavascriptInterface("NobarNative"); destroy() }; youtubeView = null; player?.release(); player = null; super.onDestroy() }
}
