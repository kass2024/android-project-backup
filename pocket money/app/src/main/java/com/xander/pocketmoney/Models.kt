package com.xander.pocketmoney

import android.app.Application
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

data class ParentAccount(val id: Int, val name: String, val phone: String, val role: String = "parent") {
    val agent: Boolean get() = role == "agent"
}

data class StudentWallet(
    val id: Int,
    val regno: String,
    val name: String,
    val className: String,
    val school: String,
    val schoolId: Int,
    val photoUrl: String,
    val cardLinked: Boolean,
    val cardDisabled: Boolean = false,
    val cardUid: String = "",
    val cardHint: String,
    val hasSpendPin: Boolean,
    val balance: Int?,
    val linked: Boolean = false,
    val relation: String = "",
)

data class FamilyWelcome(
    val registeredAs: String,
    val relation: String,
    val students: List<StudentWallet>,
)

data class LedgerLine(val name: String, val qty: Int, val unit: String)

data class LedgerItem(
    val id: Int,
    val label: String,
    val amount: Int,
    val balance: Int?,
    val status: String,
    val merchant: String,
    val message: String,
    val detail: String,
    val reason: String,
    val lines: List<LedgerLine>,
    val createdAt: String,
    val type: Int,
)

class PocketException(message: String, val reason: String = "") : Exception(message)

fun money(amount: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(amount) + " RWF"

data class PayerQuote(val debit: Int, val fee: Int)

data class ChargeQuote(val transferFee: Int, val withdrawFee: Int, val debit: Int, val fee: Int)

/** Parent total for a full student amount, including the later withdrawal fee. */
fun chargeQuote(receive: Int, percent: Double): ChargeQuote {
    val withdraw = payerTotal(receive, percent)
    val transfer = payerTotal(receive + withdraw.fee, percent)
    return ChargeQuote(transfer.fee, withdraw.fee, transfer.debit, transfer.fee + withdraw.fee)
}

/** Phone total so the pocket account receives `receive` after the gateway percent. */
fun payerTotal(receive: Int, percent: Double): PayerQuote {
    if (receive < 1 || percent <= 0.0) return PayerQuote(receive, 0)
    val rate = percent / 100.0
    val guess = kotlin.math.ceil(receive / (1.0 - rate)).toInt()
    val start = maxOf(receive + 1, guess - 8)
    for (debit in start..(guess + 40)) {
        val fee = kotlin.math.round(debit * rate).toInt()
        if (debit - fee == receive) return PayerQuote(debit, fee)
    }
    val debit = maxOf(receive + 1, guess)
    return PayerQuote(debit, debit - receive)
}

fun JSONObject.toStudent(): StudentWallet = StudentWallet(
    id = optInt("id"),
    regno = optString("regno"),
    name = optString("name"),
    className = optString("className"),
    school = optString("school"),
    schoolId = optInt("schoolId"),
    photoUrl = optString("photoUrl"),
    cardLinked = optBoolean("cardLinked"),
    cardDisabled = optBoolean("cardDisabled"),
    cardUid = optString("cardUid"),
    cardHint = optString("cardHint"),
    hasSpendPin = optBoolean("hasSpendPin"),
    balance = if (has("balance") && !isNull("balance")) optDouble("balance").toInt() else null,
    linked = optBoolean("linked"),
    relation = optString("relation"),
)

fun JSONObject.toLedger(): LedgerItem {
    val rawLines = optJSONArray("lines")
    val lines = if (rawLines == null) emptyList() else List(rawLines.length()) { index ->
        val line = rawLines.optJSONObject(index) ?: JSONObject()
        LedgerLine(line.optString("name"), line.optInt("qty"), line.optString("unit"))
    }.filter { it.name.isNotBlank() && it.qty > 0 }
    return LedgerItem(
        id = optInt("id"),
        label = optString("label"),
        amount = optInt("amount"),
        balance = if (isNull("balance")) null else optDouble("balance").toInt(),
        status = optString("status"),
        merchant = optString("merchant"),
        message = optString("message"),
        detail = optString("detail"),
        reason = optString("reason"),
        lines = lines,
        createdAt = optString("createdAt"),
        type = optInt("type"),
    )
}

fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("xander_pocket", Context.MODE_PRIVATE)

    val baseUrl: String
        get() = BuildConfig.DEFAULT_BASE_URL

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit().putString("token", value).apply()

    var lang: String
        get() = when (prefs.getString("lang", "en")) {
            "sw", "rw" -> "sw"
            "fr" -> "fr"
            else -> "en"
        }
        set(value) = prefs.edit().putString("lang", when (value) {
            "sw", "rw" -> "sw"
            "fr" -> "fr"
            else -> "en"
        }).apply()

    var langChosen: Boolean
        get() = prefs.getBoolean("lang_chosen", false)
        set(value) = prefs.edit().putBoolean("lang_chosen", value).apply()

    fun saveParent(parent: ParentAccount) {
        prefs.edit()
            .putInt("pid", parent.id)
            .putString("pname", parent.name)
            .putString("pphone", parent.phone)
            .putString("prole", parent.role)
            .apply()
    }

    fun parent(): ParentAccount? {
        val id = prefs.getInt("pid", 0)
        val name = prefs.getString("pname", "") ?: ""
        val phone = prefs.getString("pphone", "") ?: ""
        val role = prefs.getString("prole", "parent") ?: "parent"
        return if (token.isNotBlank() && id > 0) ParentAccount(id, name, phone, role) else null
    }

    fun clear() {
        prefs.edit().remove("token").remove("pid").remove("pname").remove("pphone").apply()
    }
}

class PocketApplication : Application()
