package com.meuiptv.player

import org.json.JSONObject

/** Tipos de conteúdo que o app mostra. */
object Kind {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val FAV = "fav"

    /** Id da categoria especial "Todos". */
    const val ALL = "all"
}

/** Dados de login: ou Xtream Codes (servidor/usuário/senha) ou um link de lista M3U. */
data class Account(
    val type: String,
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
) {
    val isXtream: Boolean get() = type == TYPE_XTREAM

    companion object {
        const val TYPE_XTREAM = "xtream"
        const val TYPE_M3U = "m3u"
    }
}

data class Category(val id: String, val name: String)

/**
 * Um canal, filme, série ou episódio.
 * [url] vazio (null) significa uma série do Xtream: ao abrir, mostramos os episódios.
 */
data class Item(
    val id: String,
    val name: String,
    val icon: String?,
    val kind: String,
    val url: String?,
    val categoryId: String = "",
) {
    val key: String get() = "$kind:$id"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("icon", icon ?: "")
        .put("kind", kind)
        .put("url", url ?: "")
        .put("cat", categoryId)

    companion object {
        fun fromJson(o: JSONObject) = Item(
            id = o.optString("id"),
            name = o.optString("name"),
            icon = o.optString("icon").ifEmpty { null },
            kind = o.optString("kind"),
            url = o.optString("url").ifEmpty { null },
            categoryId = o.optString("cat"),
        )
    }
}

/** Informações da tela de detalhes de um filme ou série. */
data class Details(
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val plot: String,
    val genre: String,
    val year: String?,
    val duration: String,
    /** Nota de 0 a 10 (null = sem nota). */
    val rating: Double?,
    /** Quando foi adicionado no servidor (segundos). */
    val added: Long?,
    val cast: String,
    val director: String,
)
