package com.algani.nobar.push

import android.content.Context
import com.algani.nobar.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Registers this installation with the existing NOBAR /api/fcm endpoints. */
object FcmRegistrar {
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    suspend fun register(context: Context, sessionToken: String) {
        if (sessionToken.isBlank() || !isConfigured(context)) return
        val fcmToken = FirebaseMessaging.getInstance().token.await()
        post(context, "/api/fcm/register", sessionToken, fcmToken)
        context.getSharedPreferences("nobar_push", Context.MODE_PRIVATE)
            .edit().putString("registered_fcm_token", fcmToken).apply()
    }

    suspend fun unregister(context: Context, sessionToken: String) {
        if (sessionToken.isBlank() || !isConfigured(context)) return
        val prefs = context.getSharedPreferences("nobar_push", Context.MODE_PRIVATE)
        val fcmToken = prefs.getString("registered_fcm_token", null)
            ?: FirebaseMessaging.getInstance().token.await()
        post(context, "/api/fcm/unregister", sessionToken, fcmToken)
        prefs.edit().remove("registered_fcm_token").apply()
    }

    suspend fun refreshToken(context: Context, fcmToken: String) {
        if (!isConfigured(context) || fcmToken.isBlank()) return
        val session = context.getSharedPreferences("nobar_session", Context.MODE_PRIVATE)
            .getString("token", "").orEmpty()
        if (session.isBlank()) return
        post(context, "/api/fcm/register", session, fcmToken)
        context.getSharedPreferences("nobar_push", Context.MODE_PRIVATE)
            .edit().putString("registered_fcm_token", fcmToken).apply()
    }

    private suspend fun post(context: Context, path: String, sessionToken: String, fcmToken: String) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = JSONObject().put("token", fcmToken).toString().toRequestBody(jsonType)
            val base = BuildConfig.NOBAR_BASE_URL.trimEnd('/')
            val request = Request.Builder().url(base + path)
                .header("Authorization", "Bearer $sessionToken")
                .post(body).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("FCM registration HTTP ${response.code}")
            }
        }
    }
}
