package com.algani.nobar.data

import android.os.Handler
import android.os.Looper
import com.algani.nobar.BuildConfig
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** WebSocket adapter preserving the existing NOBAR server's JSON message protocol. */
class RoomSocket(private val onMessage: (JSONObject) -> Unit, private val onStatus: (String) -> Unit) {
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var room = ""; private var name = ""; private var token = ""; private var password = ""
    private var retries = 0
    @Volatile private var stopped = false
    @Volatile var connected = false; private set

    fun connect(room: String, name: String, token: String, password: String = "") {
        this.room = room; this.name = name; this.token = token; this.password = password
        stopped = false; retries = 0; openSocket()
    }

    @Synchronized private fun openSocket() {
        if (stopped || connected) return
        val base = BuildConfig.NOBAR_BASE_URL.replace("https://", "wss://").replace("http://", "ws://")
        onStatus(if (retries == 0) "Menghubungkan…" else "Menyambungkan ulang…")
        socket = client.newWebSocket(Request.Builder().url(base).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true; retries = 0; onStatus("Terhubung")
                webSocket.send(JSONObject().put("t", "join").put("room", room).put("name", name).put("token", token).put("pw", password).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) { runCatching { onMessage(JSONObject(text)) } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false; onStatus("Koneksi terputus: ${t.message ?: "coba lagi"}"); scheduleReconnect()
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false; if (!stopped) { onStatus("Terputus; menyambungkan ulang…"); scheduleReconnect() } else onStatus("Terputus")
            }
        })
    }

    @Synchronized private fun scheduleReconnect() {
        if (stopped) return
        val delay = (1000L * (retries + 1).coerceAtMost(5)).coerceAtMost(5000L)
        retries++
        main.removeCallbacksAndMessages(this)
        main.postAtTime({ openSocket() }, this, android.os.SystemClock.uptimeMillis() + delay)
    }

    fun send(message: JSONObject) { if (connected) socket?.send(message.toString()) }
    @Synchronized fun close() {
        stopped = true; connected = false; main.removeCallbacksAndMessages(this)
        socket?.close(1000, "leave room"); socket = null
    }
}
