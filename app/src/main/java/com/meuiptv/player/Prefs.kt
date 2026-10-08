package com.meuiptv.player

import android.content.Context
import org.json.JSONArray

/** Guarda o login e os favoritos no próprio aparelho. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("meu_iptv", Context.MODE_PRIVATE)

    fun saveAccount(a: Account) {
        sp.edit()
            .putString("type", a.type)
            .putString("server", a.server)
            .putString("user", a.username)
            .putString("pass", a.password)
            .putString("m3u", a.m3uUrl)
            .apply()
    }

    fun account(): Account? {
        val type = sp.getString("type", null) ?: return null
        return Account(
            type = type,
            server = sp.getString("server", "") ?: "",
            username = sp.getString("user", "") ?: "",
            password = sp.getString("pass", "") ?: "",
            m3uUrl = sp.getString("m3u", "") ?: "",
        )
    }

    fun logout() {
        sp.edit().remove("type").apply()
    }

    fun favorites(): MutableList<Item> {
        val arr = try {
            JSONArray(sp.getString("favs", "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<Item>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { out += Item.fromJson(it) }
        }
        return out
    }

    fun favoriteKeys(): Set<String> = favorites().map { it.key }.toSet()

    /** Adiciona ou remove dos favoritos. Retorna true se ficou favoritado. */
    fun toggleFavorite(item: Item): Boolean {
        val list = favorites()
        val removed = list.removeAll { it.key == item.key }
        if (!removed) list.add(0, item)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("favs", arr.toString()).apply()
        return !removed
    }
}
