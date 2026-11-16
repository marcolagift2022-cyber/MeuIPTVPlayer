package com.meuiptv.player

import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Conversa com painéis no padrão "Xtream Codes" (player_api.php),
 * o mesmo usado pelo XCIPTV e pela maioria dos provedores.
 */
class XtreamApi(private val account: Account) {

    private val base = account.server.trimEnd('/')
    private val user = account.username
    private val pass = account.password

    private fun call(action: String? = null, extra: String = ""): String {
        var url = "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}"
        if (action != null) url += "&action=$action$extra"
        return Http.get(url)
    }

    /** Vencimento informado pelo painel no último login (segundos; 0 = sem data). */
    var expDate: Long = 0L
        private set

    /** Retorna null se o login deu certo, ou a mensagem de erro. */
    fun login(): String? {
        val o = try {
            JSONObject(call())
        } catch (e: JSONException) {
            // Ex.: DNS bloqueado devolvendo uma página qualquer. Tratado como falha de conexão.
            throw java.io.IOException("O servidor não respondeu como um painel Xtream.")
        }
        val info = o.optJSONObject("user_info") ?: return "Usuário ou senha incorretos."
        if (info.optInt("auth", 0) != 1) return "Usuário ou senha incorretos."
        val status = info.str("status").ifBlank { "Active" }
        if (!status.equals("Active", ignoreCase = true)) return "Sua conta está com status: $status"
        expDate = info.str("exp_date").toLongOrNull() ?: 0L
        return null
    }

    fun categories(kind: String): List<Category> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_categories"
            Kind.MOVIE -> "get_vod_categories"
            else -> "get_series_categories"
        }
        val arr = array(call(action))
        val out = ArrayList<Category>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out += Category(o.str("category_id"), o.str("category_name"))
        }
        return out
    }

    fun items(kind: String, categoryId: String): List<Item> {
        val action = when (kind) {
            Kind.LIVE -> "get_live_streams"
            Kind.MOVIE -> "get_vod_streams"
            else -> "get_series"
        }
        val extra = if (categoryId == Kind.ALL) "" else "&category_id=${enc(categoryId)}"
        val arr = array(call(action, extra))
        // Filmes e séries: os adicionados mais recentemente aparecem primeiro
        // (filmes pelo campo "added", séries pelo "last_modified"). Canais ficam na ordem do servidor.
        val objects = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.let { list ->
            when (kind) {
                Kind.MOVIE -> list.sortedByDescending { it.str("added").toLongOrNull() ?: 0L }
                Kind.SERIES -> list.sortedByDescending {
                    it.str("last_modified").toLongOrNull() ?: it.str("added").toLongOrNull() ?: 0L
                }
                else -> list
            }
        }
        val out = ArrayList<Item>(objects.size)
        for (o in objects) {
            val cat = o.str("category_id")
            out += when (kind) {
                Kind.LIVE -> {
                    val id = o.str("stream_id")
                    Item(id, o.str("name"), o.str("stream_icon").ifBlank { null }, kind, liveUrl(id), cat)
                }
                Kind.MOVIE -> {
                    val id = o.str("stream_id")
                    val ext = o.str("container_extension").ifBlank { "mp4" }
                    Item(id, o.str("name"), o.str("stream_icon").ifBlank { null }, kind, "$base/movie/$user/$pass/$id.$ext", cat)
                }
                else -> Item(o.str("series_id"), o.str("name"), o.str("cover").ifBlank { null }, kind, null, cat)
            }
        }
        return out
    }

    /** Lista todos os episódios de uma série, em ordem de temporada. */
    fun episodes(seriesId: String): List<Item> =
        parseEpisodes(JSONObject(call("get_series_info", "&series_id=${enc(seriesId)}")))

    /** Sinopse, nota, ano, gênero e imagens de um filme (tela de detalhes). */
    fun movieDetails(item: Item): Details {
        val o = try {
            JSONObject(call("get_vod_info", "&vod_id=${enc(item.id)}"))
        } catch (e: JSONException) {
            JSONObject()
        }
        val info = o.optJSONObject("info") ?: JSONObject()
        val data = o.optJSONObject("movie_data") ?: JSONObject()
        return details(info, item, data.str("added").toLongOrNull())
    }

    /** Detalhes da série e todos os episódios, numa chamada só. */
    fun seriesDetails(item: Item): Pair<Details, List<Item>> {
        val o = JSONObject(call("get_series_info", "&series_id=${enc(item.id)}"))
        val info = o.optJSONObject("info") ?: JSONObject()
        val added = info.str("last_modified").toLongOrNull()
        return details(info, item, added) to parseEpisodes(o)
    }

    private fun details(info: JSONObject, item: Item, added: Long?): Details {
        val backdrop = when (val b = info.opt("backdrop_path")) {
            is JSONArray -> (0 until b.length()).map { b.optString(it) }.firstOrNull { it.startsWith("http") }
            is String -> b.takeIf { it.startsWith("http") }
            else -> null
        }
        val poster = listOf("movie_image", "cover_big", "cover").map { info.str(it) }
            .firstOrNull { it.startsWith("http") } ?: item.icon
        val date = listOf("releasedate", "release_date", "releaseDate").map { info.str(it) }.firstOrNull { it.isNotBlank() }
        val year = date?.let { Regex("(19|20)\\d{2}").find(it)?.value }
            ?: Regex("\\((19|20)\\d{2}\\)").find(item.name)?.value?.trim('(', ')')
        val rating = info.str("rating").replace(',', '.').toDoubleOrNull()
            ?: info.str("rating_5based").toDoubleOrNull()?.times(2)
        val duration = info.str("duration").ifBlank {
            info.str("episode_run_time").takeIf { it.isNotBlank() && it != "0" }?.let { "$it min" } ?: ""
        }
        return Details(
            title = info.str("name").ifBlank { item.name },
            poster = poster,
            backdrop = backdrop ?: poster,
            plot = info.str("plot").ifBlank { info.str("description") },
            genre = info.str("genre"),
            year = year,
            duration = duration,
            rating = rating?.takeIf { it > 0 },
            added = added?.takeIf { it > 0 },
            cast = info.str("cast").ifBlank { info.str("actors") },
            director = info.str("director"),
        )
    }

    private fun parseEpisodes(o: JSONObject): List<Item> {
        val seasons = ArrayList<Pair<String, JSONArray>>()
        when (val eps = o.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let { seasons += k to it } }
            is JSONArray -> for (i in 0 until eps.length()) {
                eps.optJSONArray(i)?.let { seasons += (i + 1).toString() to it }
            }
        }
        seasons.sortBy { it.first.toIntOrNull() ?: 0 }

        val out = ArrayList<Item>()
        for ((seasonKey, list) in seasons) {
            for (i in 0 until list.length()) {
                val e = list.optJSONObject(i) ?: continue
                val id = e.str("id")
                val ext = e.str("container_extension").ifBlank { "mp4" }
                val season = e.str("season").ifBlank { seasonKey }
                val num = e.str("episode_num").ifBlank { (i + 1).toString() }
                val title = e.str("title").ifBlank { "Episódio $num" }
                val image = e.optJSONObject("info")?.str("movie_image")?.ifBlank { null }
                out += Item(id, "T$season · E$num — $title", image, Kind.MOVIE, "$base/series/$user/$pass/$id.$ext", season)
            }
        }
        return out
    }

    /** Programa atual e o próximo de um canal (EPG curto). */
    fun nowNext(streamId: String): String {
        val o = JSONObject(call("get_short_epg", "&stream_id=${enc(streamId)}&limit=2"))
        val arr = o.optJSONArray("epg_listings") ?: return ""
        val lines = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val title = decode(e.str("title"))
            val start = e.str("start").let { if (it.length >= 16) it.substring(11, 16) else "" }
            val prefix = if (i == 0) "Agora" else "Depois"
            lines += "$prefix  $start  $title".trim()
        }
        return lines.joinToString("\n")
    }

    private fun liveUrl(id: String) = "$base/live/$user/$pass/$id.${Settings.liveExtension}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun array(text: String): JSONArray = try {
        JSONArray(text)
    } catch (e: JSONException) {
        JSONArray() // alguns servidores devolvem {} quando a categoria está vazia
    }

    private fun decode(s: String): String = try {
        String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        s
    }
}

/** Lê um campo como texto, tratando `null` do JSON como vazio. */
private fun JSONObject.str(name: String): String =
    if (isNull(name)) "" else optString(name)
