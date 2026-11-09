package com.xander.pos

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PosException(message: String) : Exception(message)

class PosClient(baseUrl: String, private val key: String) {
    private val root = baseUrl.trim().trimEnd('/') + "/"
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Logos and photos are stored with the PC's localhost address. The till loads them from the same host it calls. */
    fun deviceUrl(url: String): String {
        if (url.isBlank()) return url
        return try {
            val target = java.net.URI(url)
            val host = target.host?.lowercase() ?: return url
            if (host != "localhost" && host != "127.0.0.1") return url
            val base = java.net.URI(root)
            java.net.URI(base.scheme, null, base.host, base.port, target.rawPath, target.rawQuery, target.rawFragment).toString()
        } catch (error: Exception) {
            url
        }
    }

    fun school(acronym: String): JSONObject =
        get(
            "api/pocket/pos/schools?acronym=" + java.net.URLEncoder.encode(acronym.trim(), "UTF-8"),
            "That school acronym was not found.",
        )

    fun savePalm(
        acronym: String,
        card: String,
        name: String,
        className: String,
        feature: String,
        studentId: Int,
        savedAt: Long,
    ): JSONObject {
        val body = JSONObject()
            .put("acronym", acronym)
            .put("card", card)
            .put("name", name)
            .put("className", className)
            .put("feature", feature)
            .put("studentId", studentId)
            .put("savedAt", savedAt)
        return post("api/pocket/pos/palm", body, "The palm could not be backed up.")
    }

    fun palms(acronym: String): JSONObject =
        get(
            "api/pocket/pos/palms?acronym=" + java.net.URLEncoder.encode(acronym.trim(), "UTF-8"),
            "Could not restore palm backups.",
        )

    fun deletePalm(acronym: String, card: String): JSONObject =
        post(
            "api/pocket/pos/palm/delete",
            JSONObject().put("acronym", acronym).put("card", card),
            "The palm backup could not be removed.",
        )

    fun today(acronym: String): JSONObject =
        get(
            "api/pocket/pos/today?acronym=" + java.net.URLEncoder.encode(acronym.trim(), "UTF-8"),
            "Could not load today's report.",
        )

    fun catalog(acronym: String): JSONObject =
        get(
            "api/pocket/pos/catalog?acronym=" + java.net.URLEncoder.encode(acronym.trim(), "UTF-8"),
            "Could not download the menu.",
        )

    fun searchStudents(acronym: String, query: String): JSONObject =
        get(
            "api/pocket/pos/students?acronym=" + java.net.URLEncoder.encode(acronym.trim(), "UTF-8") +
                "&q=" + java.net.URLEncoder.encode(query.trim(), "UTF-8"),
            "Could not search students.",
        )

    /** One download of this school's students. Typing then filters on the till. */
    fun studentIndex(acronym: String): JSONObject =
        get(
            "api/pocket/pos/students?acronym=" + java.net.URLEncoder.encode(acronym.trim(), "UTF-8") + "&all=1",
            "Could not search students.",
        )

    fun desk(acronym: String, holding: Int, waitSeconds: Int = 0): JSONObject =
        get(
            "api/pocket/pos/desk?acronym=" + java.net.URLEncoder.encode(acronym.trim(), "UTF-8") +
                "&holding=" + holding +
                "&wait=" + waitSeconds.coerceIn(0, 8),
            "Could not reach the POS desk.",
        )

    fun deskCancel(acronym: String, id: Int): JSONObject =
        post(
            "api/pocket/pos/desk/cancel",
            JSONObject().put("acronym", acronym).put("id", id),
            "Could not cancel the sale.",
        )

    fun preview(acronym: String, card: String): JSONObject =
        post(
            "api/pocket/pos/preview",
            JSONObject().put("acronym", acronym).put("card", card),
            "Card not found.",
        )

    fun charge(
        acronym: String,
        card: String,
        amount: Int,
        canteenPhone: String,
        items: String,
        lines: JSONArray,
        palmProof: String,
        clientRef: String,
    ): JSONObject {
        val body = JSONObject()
            .put("acronym", acronym)
            .put("card", card)
            .put("amount", amount)
            .put("merchant", "Canteen")
            .put("clientRef", clientRef)
            .put("canteenPhone", canteenPhone)
            .put("items", items)
            .put("lines", lines)
            .put("signature", palmProof)
        return post("api/pocket/pos/charge", body, "The canteen could not charge this card.")
    }

    private fun get(path: String, fallback: String): JSONObject {
        val request = Request.Builder()
            .url(root + path)
            .header("Accept", "application/json")
            .header("X-Pocket-Pos-Key", key)
            .get()
            .build()
        return call(request, fallback)
    }

    private fun post(path: String, body: JSONObject, fallback: String): JSONObject {
        val request = Request.Builder()
            .url(root + path)
            .header("Accept", "application/json")
            .header("X-Pocket-Pos-Key", key)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        return call(request, fallback)
    }

    private fun call(request: Request, fallback: String): JSONObject {
        val response = try {
            http.newCall(request).execute()
        } catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException) throw error
            throw PosException("Cannot reach the school server.")
        }
        val text = try {
            response.body?.string().orEmpty()
        } catch (error: Exception) {
            if (error is java.util.concurrent.CancellationException) throw error
            throw PosException("Cannot reach the school server.")
        }
        val json = try {
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (error: Exception) {
            JSONObject()
        }
        if (!response.isSuccessful) {
            val message = json.optString("message").trim()
            val snippet = text.replace(Regex("\\s+"), " ").take(100)
            android.util.Log.w("PosClient", "HTTP ${response.code} ${request.url.encodedPath} $snippet")
            throw PosException(if (message.isNotEmpty()) message else "$fallback (HTTP ${response.code})")
        }
        return json
    }
}
