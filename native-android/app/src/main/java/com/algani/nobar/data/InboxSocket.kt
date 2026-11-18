package com.algani.nobar.data

import android.os.Handler
import android.os.Looper
import com.algani.nobar.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Home-screen presence socket. Uses the original inbox.js hello/vis/invite contract. */
class InboxSocket(private val onInvite: (JSONObject) -> Unit, private val onStatus: (String) -> Unit) {
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val handler = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var token = ""
    private var hidden = false
    private var stopped = true
    private var attempts = 0

    @Synchronized fun connect(token: String) {
        if (token.isBlank()) return
        this.token = token
        stopped = false
        if (ws == null) open()
    }
    @Synchronized private fun open() {
        if (stopped || token.isBlank() || ws != null) return
        val base = BuildConfig.NOBAR_BASE_URL.replace("https://", "wss://").replace("http://", "ws://")
        ws = client.newWebSocket(Request.Builder().url(base).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempts = 0
                webSocket.send(JSONObject().put("t", "hello").put("token", token).put("h", hidden).toString())
                onStatus("Online · siap menerima undangan")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text) }.getOrNull()?.let { msg -> if (msg.optString("t") == "invite") onInvite(msg) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { failed(webSocket) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { failed(webSocket) }
        })
    }
    @Synchronized private fun failed(source: WebSocket) {
        if (ws !== source) return
        ws = null
        if (!stopped) {
            onStatus("Inbox terputus · mencoba menyambungkan")
            val wait = (1000L * (++attempts).coerceAtMost(5)).coerceAtMost(5000L)
            handler.postDelayed({ open() }, wait)
        }
    }
    fun visibility(hidden: Boolean) {
        this.hidden = hidden
        ws?.send(JSONObject().put("t", "vis").put("h", hidden).toString())
    }
    @Synchronized fun close() {
        stopped = true; handler.removeCallbacksAndMessages(null)
        ws?.close(1000, "inbox closed"); ws = null
    }
}
