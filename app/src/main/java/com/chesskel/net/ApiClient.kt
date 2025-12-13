package com.chesskel.net

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

object ApiClient {
    // Base URL (Azure)
    private const val BASE = "https://chesskelu-g4dsgafjefe6fugv.canadacentral-01.azurewebsites.net"
    private const val TAG = "ApiClient"

    private suspend fun httpRequest(path: String, method: String = "GET", body: String? = null, contentType: String = "application/json"): Pair<Int, String?> {
        return withContext(Dispatchers.IO) {
            val url = URL(BASE + path)
            var conn: HttpURLConnection? = null
            try {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/json")
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", contentType)
                    }
                }

                if (body != null) {
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let {
                    BufferedReader(InputStreamReader(it)).use { br -> br.readText() }
                }
                Pair(code, text)
            } catch (e: Exception) {
                Log.w(TAG, "httpRequest error", e)
                Pair(-1, null)
            } finally {
                conn?.disconnect()
            }
        }
    }

    suspend fun uploadImage(context: Context, imageUri: Uri, userId: Long): Pair<String?, String?> {
        return withContext(Dispatchers.IO) {
            val url = URL(BASE + "/upload/image?userId=$userId")
            var conn: HttpURLConnection? = null
            try {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Accept", "application/json")
                }

                val boundary = UUID.randomUUID().toString()
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                val outputStream = DataOutputStream(conn.outputStream)
                outputStream.writeBytes("--$boundary\r\n")
                outputStream.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"profile.jpg\"\r\n")
                outputStream.writeBytes("Content-Type: image/jpeg\r\n\r\n")

                context.contentResolver.openInputStream(imageUri)?.use { input ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }

                outputStream.writeBytes("\r\n--$boundary--\r\n")
                outputStream.flush()
                outputStream.close()

                val code = conn.responseCode
                if (code in 200..299) {
                    val stream = conn.inputStream
                    val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    val json = JSONObject(text)
                    val uploadedUrl = json.optString("imageUrl").let { if (it == JSONObject.NULL) null else it }
                    Pair(uploadedUrl, null)
                } else {
                    val errorStream = conn.errorStream
                    val errorText = errorStream?.let { BufferedReader(InputStreamReader(it)).use { it.readText() } } ?: "HTTP $code"
                    Log.w(TAG, "Upload failed: $code $errorText")
                    Pair(null, errorText)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload error", e)
                Pair(null, e.message ?: "Unknown error")
            } finally {
                conn?.disconnect()
            }
        }
    }

    suspend fun getUserByEmail(email: String): JSONObject? {
        val enc = URLEncoder.encode(email, "utf-8")
        val (code, body) = httpRequest("/users/by-email/$enc")
        if (code == 200 && body != null) return JSONObject(body)
        return null
    }

    suspend fun createUser(nombre: String, email: String, passwordHash: String): JSONObject? {
        val json = JSONObject().apply {
            put("nombre", nombre)
            put("email", email)
            put("passwordHash", passwordHash)
        }
        val (code, body) = httpRequest("/users", "POST", json.toString())
        if (code in 200..299 && body != null) return JSONObject(body)
        return null
    }

    suspend fun login(email: String, passwordHash: String): JSONObject? {
        val json = JSONObject().apply {
            put("email", email)
            put("passwordHash", passwordHash)
        }
        val (code, body) = httpRequest("/auth/login", "POST", json.toString())
        if (code in 200..299 && body != null) return JSONObject(body)
        return null
    }

    suspend fun updateUserProfile(remoteId: Long, profileImageUrl: String?, location: String?): JSONObject? {
        val json = JSONObject().apply {
            put("profileImageUrl", profileImageUrl ?: JSONObject.NULL)
            put("location", location ?: JSONObject.NULL)
        }
        val (code, body) = httpRequest("/users/$remoteId/profile", "PATCH", json.toString())
        if (code in 200..299 && body != null) return JSONObject(body)
        return null
    }

    suspend fun upsertProfileByEmail(email: String, nombre: String?, passwordHash: String?, profileImageUrl: String?, location: String?): JSONObject? {
        val json = JSONObject().apply {
            if (profileImageUrl != null) put("profileImageUrl", profileImageUrl)
            if (location != null) put("location", location)
            if (nombre != null) put("nombre", nombre)
            if (passwordHash != null) put("passwordHash", passwordHash)
        }
        // If no fields to update, don't make the request
        if (json.length() == 0) return null
        val enc = URLEncoder.encode(email, "utf-8")
        val (code, body) = httpRequest("/users/by-email/$enc/profile", "PATCH", json.toString())
        if (code in 200..299 && body != null) return JSONObject(body)
        return null
    }
}
