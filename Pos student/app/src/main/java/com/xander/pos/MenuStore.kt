package com.xander.pos

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MenuItem(val id: String, val name: String, val price: Int, val stock: Int, val unit: String = "")

data class SchoolBrand(
    val id: Int = 0,
    val name: String = "",
    val acronym: String = "",
    val slogan: String = "",
    val address: String = "",
    val phone: String = "",
    val pobox: String = "",
    val email: String = "",
    val logoUrl: String = "",
    val headerColor: String = "",
    val mainColor: String = "",
)

class MenuStore(context: Context) {
    private val prefs = context.getSharedPreferences("xander_pos", Context.MODE_PRIVATE)

    var schoolId: Int
        get() = prefs.getInt("school", 0)
        set(value) = prefs.edit().putInt("school", value).apply()

    var schoolName: String
        get() = prefs.getString("school_name", "") ?: ""
        set(value) = prefs.edit().putString("school_name", value).apply()

    var acronymConfirmed: Boolean
        get() = prefs.getBoolean("acronym_ok", false)
        set(value) = prefs.edit().putBoolean("acronym_ok", value).apply()

    var canteenPhone: String
        get() = prefs.getString("canteen", "") ?: ""
        set(value) = prefs.edit().putString("canteen", value.trim()).apply()

    var soldToday: Int
        get() = if (prefs.getString("sold_day", "") == today()) prefs.getInt("sold", 0) else 0
        set(value) = prefs.edit().putString("sold_day", today()).putInt("sold", value).apply()

    var brand: SchoolBrand
        get() {
            val raw = prefs.getString("brand", "") ?: ""
            if (raw.isBlank()) return SchoolBrand(id = schoolId, name = schoolName)
            return try {
                brandFrom(JSONObject(raw))
            } catch (error: Exception) {
                SchoolBrand(id = schoolId, name = schoolName)
            }
        }
        set(value) {
            prefs.edit()
                .putInt("school", value.id)
                .putString("school_name", value.name)
                .putString("brand", JSONObject()
                    .put("id", value.id)
                    .put("name", value.name)
                    .put("acronym", value.acronym)
                    .put("slogan", value.slogan)
                    .put("address", value.address)
                    .put("phone", value.phone)
                    .put("pobox", value.pobox)
                    .put("email", value.email)
                    .put("logoUrl", value.logoUrl)
                    .put("headerColor", value.headerColor)
                    .put("mainColor", value.mainColor)
                    .toString())
                .apply()
        }

    fun items(): List<MenuItem> {
        val raw = prefs.getString("menu", "") ?: ""
        if (raw.isBlank()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (error: Exception) {
            return emptyList()
        }
        val list = mutableListOf<MenuItem>()
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val name = row.optString("name").trim()
            val price = row.optInt("price")
            if (name.isNotBlank() && price > 0) {
                list.add(MenuItem(row.optString("id", "i$i"), name, price, row.optInt("stock"), row.optString("unit")))
            }
        }
        return list
    }

    fun save(items: List<MenuItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("name", item.name).put("price", item.price).put("stock", item.stock).put("unit", item.unit))
        }
        prefs.edit().putString("menu", array.toString()).apply()
    }

    private fun today(): String {
        val now = java.util.Calendar.getInstance()
        return "%04d-%02d-%02d".format(now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH) + 1, now.get(java.util.Calendar.DAY_OF_MONTH))
    }
}

fun brandFrom(row: JSONObject): SchoolBrand = SchoolBrand(
    id = row.optInt("id"),
    name = row.optString("name"),
    acronym = row.optString("acronym"),
    slogan = row.optString("slogan"),
    address = row.optString("address"),
    phone = row.optString("phone"),
    pobox = row.optString("pobox"),
    email = row.optString("email"),
    logoUrl = row.optString("logoUrl"),
    headerColor = row.optString("headerColor"),
    mainColor = row.optString("mainColor"),
)
