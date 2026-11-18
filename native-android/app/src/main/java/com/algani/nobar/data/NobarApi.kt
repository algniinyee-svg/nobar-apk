package com.algani.nobar.data

import com.algani.nobar.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NobarApi {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    suspend fun post(path: String, data: JSONObject, token: String? = null): JSONObject =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val builder = Request.Builder().url(BuildConfig.NOBAR_BASE_URL + path).post(data.toString().toRequestBody(jsonType))
            if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
            http.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val result = if (body.isBlank()) JSONObject() else JSONObject(body)
                if (!response.isSuccessful) throw IllegalStateException(result.optString("error", "HTTP ${response.code}"))
                result
            }
        }

    suspend fun get(path: String, token: String): JSONObject = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val req = Request.Builder().url(BuildConfig.NOBAR_BASE_URL + path).header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val result = if (body.isBlank()) JSONObject() else JSONObject(body)
            if (!response.isSuccessful) throw IllegalStateException(result.optString("error", "HTTP ${response.code}"))
            result
        }
    }
    suspend fun delete(path: String, token: String): JSONObject = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val req = Request.Builder().url(BuildConfig.NOBAR_BASE_URL + path).header("Authorization", "Bearer $token").delete().build()
        http.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val result = if (body.isBlank()) JSONObject() else JSONObject(body)
            if (!response.isSuccessful) throw IllegalStateException(result.optString("error", "HTTP ${response.code}"))
            result
        }
    }


    suspend fun getBytes(path: String, token: String): ByteArray = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val req = Request.Builder().url(BuildConfig.NOBAR_BASE_URL + path).header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException(if (response.code == 404) "Wallpaper belum diatur" else "HTTP ${response.code}")
            response.body?.bytes() ?: throw IllegalStateException("Respons gambar kosong")
        }
    }

    suspend fun uploadJpeg(path: String, bytes: ByteArray, token: String): JSONObject = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val req = Request.Builder().url(BuildConfig.NOBAR_BASE_URL + path).header("Authorization", "Bearer $token").post(body).build()
        http.newCall(req).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (!response.isSuccessful) throw IllegalStateException(result.optString("error", "HTTP ${response.code}"))
            result
        }
    }

    suspend fun iceServers(token: String): org.json.JSONArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val req = Request.Builder().url(BuildConfig.NOBAR_BASE_URL + "/api/ice")
            .header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return@withContext null
            JSONObject(body).optJSONArray("iceServers")
        }
    }

    suspend fun me(token: String): JSONObject = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val req = Request.Builder().url(BuildConfig.NOBAR_BASE_URL + "/api/me").header("Authorization", "Bearer $token").get().build()
        http.newCall(req).execute().use { response -> JSONObject(response.body?.string().orEmpty()) }
    }
}
