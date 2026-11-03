package com.vault.pass

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class VaultEntry(
    val id: String = UUID.randomUUID().toString(),
    var site: String,
    var username: String,
    var password: String,
    var notes: String = "",
    var pinned: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("site", site)
        .put("username", username)
        .put("password", password)
        .put("notes", notes)
        .put("pinned", pinned)

    companion object {
        fun fromJson(o: JSONObject) = VaultEntry(
            id = o.optString("id", UUID.randomUUID().toString()),
            site = o.optString("site"),
            username = o.optString("username"),
            password = o.optString("password"),
            notes = o.optString("notes"),
            pinned = o.optBoolean("pinned", false)
        )
    }
}

fun List<VaultEntry>.toJson(): String {
    val arr = JSONArray()
    forEach { arr.put(it.toJson()) }
    return arr.toString()
}

fun vaultFromJson(raw: String?): MutableList<VaultEntry> {
    if (raw.isNullOrBlank()) return mutableListOf()
    val arr = JSONArray(raw)
    return List(arr.length()) { VaultEntry.fromJson(arr.getJSONObject(it)) }.toMutableList()
}
