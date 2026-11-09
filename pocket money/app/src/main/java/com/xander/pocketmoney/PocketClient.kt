package com.xander.pocketmoney

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PocketClient(baseUrl: String, private val token: String) {
    private val root = baseUrl.trim().trimEnd('/') + "/"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun health(): JSONObject = get("api/pocket/health")
    fun registerStart(name: String, phone: String, pin: String, appHash: String): JSONObject =
        post(
            "api/pocket/register-start",
            JSONObject().put("name", name).put("phone", phone).put("pin", pin).put("appHash", appHash),
        )

    fun registerConfirm(phone: String, code: String): JSONObject =
        post("api/pocket/register-confirm", JSONObject().put("phone", phone).put("code", code))

    fun login(phone: String, pin: String): JSONObject =
        post("api/pocket/login", JSONObject().put("phone", phone).put("pin", pin))

    fun recover(phone: String, appHash: String): JSONObject =
        post("api/pocket/recover", JSONObject().put("phone", phone).put("appHash", appHash))

    fun recoverConfirm(phone: String, code: String, pin: String): JSONObject =
        post("api/pocket/recover-confirm", JSONObject().put("phone", phone).put("code", code).put("pin", pin))

    fun lookup(regno: String): JSONObject = post("api/pocket/lookup", JSONObject().put("regno", regno))
    fun link(studentId: Int, spendPin: String): JSONObject =
        post("api/pocket/link", JSONObject().put("studentId", studentId).put("spendPin", spendPin))

    fun unlink(studentId: Int): JSONObject = post("api/pocket/unlink", JSONObject().put("studentId", studentId))
    fun wallets(): JSONObject = get("api/pocket/wallets")
    fun ledger(studentId: Int): JSONObject = get("api/pocket/ledger/$studentId")
    fun spendPin(studentId: Int, pin: String, oldPin: String): JSONObject =
        post("api/pocket/spend-pin", JSONObject().put("studentId", studentId).put("pin", pin).put("oldPin", oldPin))

    fun topup(studentId: Int, amount: Int, phone: String, mno: String): JSONObject =
        post(
            "api/pocket/topup",
            JSONObject().put("studentId", studentId).put("amount", amount).put("phone", phone).put("mno", mno),
        )

    fun topupStatus(paymentId: Int): JSONObject = get("api/pocket/topup/$paymentId")
    fun logout() {
        runCatching { post("api/pocket/logout", JSONObject()) }
    }

    private fun get(path: String): JSONObject = call(Request.Builder().url(root + path).get())
    private fun post(path: String, body: JSONObject): JSONObject =
        call(Request.Builder().url(root + path).post(body.toString().toRequestBody(jsonType)))

    private fun call(builder: Request.Builder): JSONObject {
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        builder.header("Accept", "application/json")
        builder.header("Cache-Control", "no-cache")
        val response = try {
            http.newCall(builder.build()).execute()
        } catch (error: Exception) {
            throw PocketException("Cannot reach the school server.")
        }
        val text = response.body?.string().orEmpty().trim().removePrefix("\uFEFF")
        val json = runCatching { JSONObject(text) }.getOrElse {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            val embedded = if (start >= 0 && end > start) runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() else null
            if (embedded != null) return@getOrElse embedded
            val plain = text.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
            throw PocketException(
                when {
                    plain.contains("already has a Pocket account") -> "This phone already has a Pocket account. Sign in instead."
                    plain.isNotBlank() && plain.length < 180 -> plain
                    response.isSuccessful -> "Unexpected server response."
                    else -> "The school server rejected the request."
                },
            )
        }
        if (!response.isSuccessful) {
            throw PocketException(json.optString("message", "Request failed."), json.optString("reason"))
        }
        return json
    }

    companion object {
        private val http = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
