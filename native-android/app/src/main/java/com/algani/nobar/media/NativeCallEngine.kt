package com.algani.nobar.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Native WebRTC engine using NOBAR's existing signaling envelope:
 * { t:"sig", to:<peerId>, data:{ d:{type,sdp} } }
 * { t:"sig", to:<peerId>, data:{ c:{sdpMid,sdpMLineIndex,candidate} } }
 *
 * Ask Android for RECORD_AUDIO (and CAMERA for video) before startMedia().
 */
class NativeCallEngine(
    context: Context,
    private val myId: Int,
    private val sendSignal: (Int, JSONObject) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onRemoteVideo: (Int, VideoTrack?) -> Unit = { _, _ -> },
    private val serverIceConfig: org.json.JSONArray? = null
) {
    private data class Peer(
        val pc: PeerConnection,
        val polite: Boolean,
        var makingOffer: Boolean = false,
        var ignoreOffer: Boolean = false,
        var remoteReady: Boolean = false,
        val pendingIce: MutableList<IceCandidate> = mutableListOf()
    )

    private val appContext = context.applicationContext
    private val peers = ConcurrentHashMap<Int, Peer>()
    private val egl = EglBase.create()
    private val factory: PeerConnectionFactory
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    @Volatile private var closed = false

    val localVideoTrack: VideoTrack? get() = videoTrack
    val eglContext: EglBase.Context get() = egl.eglBaseContext
    val microphoneEnabled: Boolean get() = audioTrack?.enabled() == true
    val cameraEnabled: Boolean get() = videoTrack?.enabled() == true

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    @Synchronized
    fun startMedia(withCamera: Boolean) {
        check(!closed) { "Call engine already closed" }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onStatus("Izin mikrofon diperlukan")
            return
        }
        if (audioTrack == null) {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            audioSource = factory.createAudioSource(constraints)
            audioTrack = factory.createAudioTrack("nobar-audio-$myId", audioSource).apply { setEnabled(true) }
        }
        if (withCamera && videoTrack == null &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
        peers.values.forEach { attachLocalTracks(it.pc) }
        onStatus(if (videoTrack != null) "Mikrofon dan kamera aktif" else "Panggilan suara aktif")
    }

    @Synchronized
    private fun startCamera() {
        val enumerator = Camera2Enumerator(appContext)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
        if (cameraName == null) {
            onStatus("Kamera tidak ditemukan")
            return
        }
        val camera = enumerator.createCapturer(cameraName, null) as? CameraVideoCapturer ?: return
        val source = factory.createVideoSource(false)
        val helper = SurfaceTextureHelper.create("NobarCamera", egl.eglBaseContext)
        camera.initialize(helper, appContext, source.capturerObserver)
        try {
            camera.startCapture(640, 480, 15)
        } catch (e: Exception) {
            runCatching { camera.dispose() }
            helper.dispose()
            source.dispose()
            onStatus("Kamera gagal dimulai: ${e.message}")
            return
        }
        capturer = camera
        textureHelper = helper
        videoSource = source
        videoTrack = factory.createVideoTrack("nobar-video-$myId", source).apply { setEnabled(true) }
    }

    fun toggleMicrophone(): Boolean {
        val track = audioTrack ?: return false
        track.setEnabled(!track.enabled())
        onStatus(if (track.enabled()) "Mikrofon aktif" else "Mikrofon dibisukan")
        return track.enabled()
    }

    fun toggleCamera(): Boolean {
        val track = videoTrack ?: return false
        track.setEnabled(!track.enabled())
        onStatus(if (track.enabled()) "Kamera aktif" else "Kamera mati")
        return track.enabled()
    }

    @Synchronized
    fun updateMembers(ids: List<Int>) {
        if (closed) return
        val wanted = ids.filter { it != myId }.toSet()
        peers.keys.toList().filterNot { it in wanted }.forEach { id ->
            peers.remove(id)?.let { peer -> onRemoteVideo(id, null); peer.pc.close(); peer.pc.dispose() }
        }
        wanted.forEach { id -> if (!peers.containsKey(id)) createPeer(id) }
    }

    private fun createPeer(id: Int) {
        val iceServers = buildList {
            val configured = serverIceConfig
            if (configured != null) for (index in 0 until configured.length()) {
                val item = configured.optJSONObject(index) ?: continue
                val rawUrls = item.opt("urls")
                val urls = when (rawUrls) {
                    is org.json.JSONArray -> (0 until rawUrls.length()).mapNotNull { rawUrls.optString(it).takeIf(String::isNotBlank) }
                    is String -> listOf(rawUrls)
                    else -> emptyList()
                }
                if (urls.isEmpty()) continue
                val builder = PeerConnection.IceServer.builder(urls)
                val username = item.optString("username")
                val credential = item.optString("credential")
                if (username.isNotBlank()) builder.setUsername(username)
                if (credential.isNotBlank()) builder.setPassword(credential)
                add(builder.createIceServer())
            }
            add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
            add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer())
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                onStatus("Peserta #$id: ICE ${state.name.lowercase()}")
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                onStatus("Peserta #$id: koneksi ${state.name.lowercase()}")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) {
                val c = JSONObject()
                    .put("sdpMid", candidate.sdpMid)
                    .put("sdpMLineIndex", candidate.sdpMLineIndex)
                    .put("candidate", candidate.sdp)
                sendSignal(id, JSONObject().put("c", c))
            }
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() { negotiate(id) }
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<MediaStream>) {
                (receiver.track() as? VideoTrack)?.let { track -> onRemoteVideo(id, track) }
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                (transceiver.receiver.track() as? VideoTrack)?.let { track -> onRemoteVideo(id, track) }
            }
        }
        val pc = factory.createPeerConnection(config, observer) ?: run {
            onStatus("Gagal membuat koneksi ke peserta #$id")
            return
        }
        val peer = Peer(pc, polite = myId > id)
        peers[id] = peer
        attachLocalTracks(pc)
    }

    private fun attachLocalTracks(pc: PeerConnection) {
        audioTrack?.let { track ->
            if (pc.senders.none { it.track()?.id() == track.id() }) pc.addTrack(track, listOf("nobar"))
        }
        videoTrack?.let { track ->
            if (pc.senders.none { it.track()?.id() == track.id() }) pc.addTrack(track, listOf("nobar"))
        }
    }

    private fun negotiate(id: Int) {
        val peer = peers[id] ?: return
        if (closed || peer.pc.signalingState() != PeerConnection.SignalingState.STABLE) return
        peer.makingOffer = true
        peer.pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(offer: SessionDescription) {
                peer.pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        sendSignal(id, JSONObject().put("d", JSONObject()
                            .put("type", offer.type.canonicalForm())
                            .put("sdp", offer.description)))
                        peer.makingOffer = false
                    }
                    override fun onSetFailure(error: String) {
                        peer.makingOffer = false
                        onStatus("Offer gagal: $error")
                    }
                    override fun onCreateSuccess(s: SessionDescription) = Unit
                    override fun onCreateFailure(s: String) = Unit
                }, offer)
            }
            override fun onCreateFailure(error: String) {
                peer.makingOffer = false
                onStatus("Negosiasi gagal: $error")
            }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String) = Unit
        }, MediaConstraints())
    }

    fun handleSignal(from: Int, data: JSONObject) {
        if (closed) return
        val peer = peers[from] ?: run { createPeer(from); peers[from] } ?: return
        val d = data.optJSONObject("d")
        if (d != null) {
            val type = runCatching { SessionDescription.Type.fromCanonicalForm(d.optString("type")) }.getOrNull() ?: return
            val collision = type == SessionDescription.Type.OFFER &&
                (peer.makingOffer || peer.pc.signalingState() != PeerConnection.SignalingState.STABLE)
            peer.ignoreOffer = !peer.polite && collision
            if (peer.ignoreOffer) return
            val remote = SessionDescription(type, d.optString("sdp"))
            peer.pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    peer.remoteReady = true
                    synchronized(peer.pendingIce) {
                        peer.pendingIce.forEach { peer.pc.addIceCandidate(it) }
                        peer.pendingIce.clear()
                    }
                    if (type == SessionDescription.Type.OFFER) {
                        peer.pc.createAnswer(object : SdpObserver {
                            override fun onCreateSuccess(answer: SessionDescription) {
                                peer.pc.setLocalDescription(object : SdpObserver {
                                    override fun onSetSuccess() {
                                        sendSignal(from, JSONObject().put("d", JSONObject()
                                            .put("type", answer.type.canonicalForm())
                                            .put("sdp", answer.description)))
                                    }
                                    override fun onSetFailure(error: String) { onStatus("Answer gagal: $error") }
                                    override fun onCreateSuccess(s: SessionDescription) = Unit
                                    override fun onCreateFailure(s: String) = Unit
                                }, answer)
                            }
                            override fun onCreateFailure(error: String) { onStatus("Gagal membuat answer: $error") }
                            override fun onSetSuccess() = Unit
                            override fun onSetFailure(error: String) = Unit
                        }, MediaConstraints())
                    }
                }
                override fun onSetFailure(error: String) { onStatus("SDP ditolak: $error") }
                override fun onCreateSuccess(s: SessionDescription) = Unit
                override fun onCreateFailure(s: String) = Unit
            }, remote)
        }
        val c = data.optJSONObject("c")
        if (c != null) {
            val candidate = IceCandidate(c.optString("sdpMid"), c.optInt("sdpMLineIndex"), c.optString("candidate"))
            if (peer.remoteReady) peer.pc.addIceCandidate(candidate)
            else synchronized(peer.pendingIce) { peer.pendingIce.add(candidate) }
        }
    }

    fun muteByHost() {
        audioTrack?.setEnabled(false)
        onStatus("Mikrofon dibisukan oleh host")
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        peers.forEach { (id, peer) -> onRemoteVideo(id, null); runCatching { peer.pc.close(); peer.pc.dispose() } }
        peers.clear()
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { textureHelper?.dispose() }
        videoTrack?.dispose()
        videoSource?.dispose()
        audioTrack?.dispose()
        audioSource?.dispose()
        factory.dispose()
        egl.release()
        onStatus("Panggilan berakhir")
    }
}
