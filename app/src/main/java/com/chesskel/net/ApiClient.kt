package com.chesskel.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

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

    suspend fun updateUserProfile(remoteId: Long, profileImageUri: String?, location: String?): JSONObject? {
        val json = JSONObject().apply {
            put("profileImageUri", profileImageUri ?: JSONObject.NULL)
            put("location", location ?: JSONObject.NULL)
        }
        val (code, body) = httpRequest("/users/$remoteId/profile", "PATCH", json.toString())
        if (code in 200..299 && body != null) return JSONObject(body)
        return null
    }

    suspend fun upsertProfileByEmail(email: String, nombre: String?, passwordHash: String?, profileImageUri: String?, location: String?): JSONObject? {
        val json = JSONObject().apply {
            put("profileImageUri", profileImageUri ?: JSONObject.NULL)
            put("location", location ?: JSONObject.NULL)
            if (nombre != null) put("nombre", nombre)
            if (passwordHash != null) put("passwordHash", passwordHash)
        }
        val enc = URLEncoder.encode(email, "utf-8")
        val (code, body) = httpRequest("/users/by-email/$enc/profile", "PATCH", json.toString())
        if (code in 200..299 && body != null) return JSONObject(body)
        return null
    }
}
