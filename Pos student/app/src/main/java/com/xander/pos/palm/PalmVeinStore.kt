package com.xander.pos.palm

import android.content.Context
import com.xander.pos.reader.CardUid
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** One feature string holds the left palm, and the right palm when both were recorded. */
fun packPalms(left: String, right: String): String {
    val l = left.trim()
    val r = right.trim()
    if (r.isBlank()) return l
    return "L:$l|R:$r"
}

fun unpackPalms(feature: String): Pair<String, String> {
    val raw = feature.trim()
    val mark = "|R:"
    if (raw.startsWith("L:") && raw.contains(mark)) {
        val body = raw.removePrefix("L:")
        val cut = body.indexOf(mark)
        if (cut > 32) return body.substring(0, cut) to body.substring(cut + mark.length)
    }
    return raw to ""
}

/** Every infrared sample saved for one student. Older records may still be left and right. */
fun palmSamples(feature: String): List<String> {
    val raw = feature.trim()
    if (raw.startsWith("1:") && raw.contains("|2:")) {
        return raw.split('|').mapNotNull { part ->
            val cut = part.indexOf(':')
            if (cut <= 0) return@mapNotNull null
            part.substring(cut + 1).trim().takeIf { it.length > 32 }
        }
    }
    val (left, right) = unpackPalms(raw)
    return listOf(left, right).filter { it.length > 32 }
}

fun packSamples(features: List<String>): String {
    val clean = features.map { it.trim() }.filter { it.length > 32 }
    if (clean.isEmpty()) return ""
    if (clean.size == 1) return clean[0]
    return clean.mapIndexed { index, sample -> "${index + 1}:$sample" }.joinToString("|")
}

data class PalmRecord(
    val uid: String,
    val name: String,
    val className: String,
    val feature: String,
    val savedAt: Long,
    val studentId: Int = 0,
    val syncedAt: Long = 0,
)

/** Palm templates stay on this till and are backed up to the school server. */
class PalmVeinStore(context: Context) {
    private val file = File(context.filesDir, "palm-veins.json")
    private val tombFile = File(context.filesDir, "palm-tombstones.json")
    private var memory: MutableList<PalmRecord>? = null
    /** Rewriting 1000 palm templates must not block the capture screen. */
    private val disk = Executors.newSingleThreadExecutor()

    @Synchronized
    fun all(): List<PalmRecord> = load().toList()

    @Synchronized
    fun replaceAll(records: List<PalmRecord>) {
        memory = records.sortedBy { it.name.lowercase() }.toMutableList()
        persist()
    }

    private fun load(): MutableList<PalmRecord> {
        memory?.let { return it }
        memory = readFile().toMutableList()
        return memory!!
    }

    private fun readFile(): List<PalmRecord> {
        if (!file.exists()) return emptyList()
        val text = try {
            file.readText()
        } catch (error: Exception) {
            return emptyList()
        }
        val array = try {
            JSONArray(text)
        } catch (error: Exception) {
            return emptyList()
        }
        val list = mutableListOf<PalmRecord>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val uid = row.optString("uid").trim().uppercase()
            val feature = row.optString("feature")
            if (uid.length < 4 || feature.isBlank()) continue
            list.add(
                PalmRecord(
                    uid = uid,
                    name = row.optString("name"),
                    className = row.optString("className"),
                    feature = feature,
                    savedAt = row.optLong("savedAt"),
                    studentId = row.optInt("studentId"),
                    syncedAt = row.optLong("syncedAt"),
                ),
            )
        }
        return list.sortedBy { it.name.lowercase() }
    }

    @Synchronized
    fun find(uid: String): PalmRecord? {
        val direct = load().firstOrNull { it.uid.equals(uid, ignoreCase = true) }
        if (direct != null) return direct
        val wanted = CardUid.lookupVariants(uid).map { it.uppercase() }.toSet()
        if (wanted.isEmpty()) return null
        return load().firstOrNull { record ->
            record.uid.uppercase() in wanted || CardUid.lookupVariants(record.uid).any { it.uppercase() in wanted }
        }
    }

    @Synchronized
    fun save(record: PalmRecord) {
        val uid = record.uid.uppercase()
        val next = load()
        val keys = variantKeys(uid)
        next.removeAll { row -> row.uid.uppercase() in keys || variantKeys(row.uid).any { it in keys } }
        next.add(record.copy(uid = uid))
        next.sortBy { it.name.lowercase() }
        persist()
        tomb(tombstones().filterNot { it.uppercase() in keys }.toSet())
    }

    @Synchronized
    fun delete(uid: String) {
        val keys = variantKeys(uid)
        removeRows(keys)
        if (keys.isNotEmpty()) tomb(tombstones() + keys)
    }

    /** The server already deleted this palm. Drop the local copy and do not send it back. */
    @Synchronized
    fun forget(uid: String) {
        val keys = variantKeys(uid)
        removeRows(keys)
        tomb(tombstones().filterNot { it.uppercase() in keys }.toSet())
    }

    @Synchronized
    fun markSynced(uid: String, at: Long) {
        val current = find(uid) ?: return
        save(current.copy(syncedAt = at))
    }

    @Synchronized
    fun tombstones(): Set<String> {
        if (!tombFile.exists()) return emptySet()
        val array = try {
            JSONArray(tombFile.readText())
        } catch (error: Exception) {
            return emptySet()
        }
        val keys = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val uid = array.optString(i).trim().uppercase()
            if (uid.length >= 4) keys.add(uid)
        }
        return keys
    }

    @Synchronized
    fun clearTombstone(uid: String) {
        val keys = variantKeys(uid)
        tomb(tombstones().filterNot { it.uppercase() in keys }.toSet())
    }

    private fun removeRows(keys: Set<String>) {
        if (keys.isEmpty()) return
        val next = load()
        next.removeAll { row -> row.uid.uppercase() in keys || variantKeys(row.uid).any { it in keys } }
        persist()
    }

    private fun variantKeys(uid: String): Set<String> {
        val keys = CardUid.lookupVariants(uid).map { it.trim().uppercase() }.filter { it.length >= 4 }.toSet()
        if (keys.isNotEmpty()) return keys
        val key = uid.trim().uppercase()
        return if (key.length >= 4) setOf(key) else emptySet()
    }

    private fun persist() {
        val snapshot = load().toList()
        disk.execute { write(snapshot) }
    }

    private fun write(records: List<PalmRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("uid", record.uid)
                    .put("name", record.name)
                    .put("className", record.className)
                    .put("feature", record.feature)
                    .put("savedAt", record.savedAt)
                    .put("studentId", record.studentId)
                    .put("syncedAt", record.syncedAt),
            )
        }
        file.writeText(array.toString())
    }

    private fun tomb(keys: Set<String>) {
        val array = JSONArray()
        keys.forEach { array.put(it) }
        tombFile.writeText(array.toString())
    }
}
