package com.xander.pocketmoney

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.core.content.ContextCompat
import java.security.MessageDigest

object SmsOtp {
    fun appHash(context: Context): String {
        return runCatching {
            val packageName = context.packageName
            val info = if (Build.VERSION.SDK_INT >= 28) {
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            } ?: return ""
            val signature = signatures.firstOrNull() ?: return ""
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$packageName ${signature.toCharsString()}".toByteArray(Charsets.UTF_8))
            Base64.encodeToString(digest.copyOf(9), Base64.NO_PADDING or Base64.NO_WRAP).take(11)
        }.getOrDefault("")
    }

    fun inboxCode(context: Context, sinceMs: Long): String? {
        if (!granted(context, android.Manifest.permission.READ_SMS)) return null
        val cursor = context.contentResolver.query(
            android.net.Uri.parse("content://sms/inbox"),
            arrayOf("body", "date"),
            null,
            null,
            "date DESC",
        ) ?: return null
        cursor.use {
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")
            var seen = 0
            while (it.moveToNext() && seen < 15) {
                seen++
                val body = if (bodyIdx >= 0) it.getString(bodyIdx).orEmpty() else ""
                val at = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                if (at in 1 until sinceMs) continue
                if (!body.contains("Pocket", ignoreCase = true)) continue
                return codeIn(body) ?: continue
            }
        }
        return null
    }

    fun codeIn(body: String): String? = Regex("(?<!\\d)(\\d{6})(?!\\d)").find(body)?.groupValues?.getOrNull(1)

    @SuppressLint("MissingPermission")
    fun simPhone(context: Context): String {
        val allowed = granted(context, android.Manifest.permission.READ_PHONE_NUMBERS) ||
            granted(context, android.Manifest.permission.READ_PHONE_STATE)
        if (!allowed) return ""
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return ""
        val lines = try {
            manager.activeSubscriptionInfoList
        } catch (_: SecurityException) {
            null
        } ?: return ""
        val phone = context.getSystemService(TelephonyManager::class.java)
        for (info in lines) {
            val fromCard = if (Build.VERSION.SDK_INT >= 33) localPhone(info.number) else ""
            if (fromCard.isNotBlank()) return fromCard
            val line = try {
                phone?.createForSubscriptionId(info.subscriptionId)?.line1Number
            } catch (_: SecurityException) {
                null
            }
            val local = localPhone(line)
            if (local.isNotBlank()) return local
        }
        return ""
    }

    private fun localPhone(raw: String?): String {
        val digits = raw.orEmpty().filter { it.isDigit() }
        return when {
            digits.startsWith("250") && digits.length >= 12 -> "0" + digits.substring(3, 12)
            digits.startsWith("07") && digits.length >= 10 -> digits.take(10)
            digits.length == 9 && digits.startsWith("7") -> "0$digits"
            else -> ""
        }
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
