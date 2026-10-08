package com.meuiptv.player

/**
 * Ponto único de acesso aos dados: decide se busca no Xtream ou na lista M3U
 * e guarda em memória o que já foi carregado (para não baixar de novo).
 * Todas as funções fazem acesso à internet: chame fora da thread principal.
 */
object Repository {

    var account: Account? = null
        private set

    private var m3u: List<Item>? = null
    private val cache = HashMap<String, List<Item>>()

    @Synchronized
    fun init(a: Account) {
        if (a != account) {
            account = a
            m3u = null
            synchronized(cache) { cache.clear() }
        }
    }

    /** Esquece as listas guardadas: o próximo acesso baixa tudo de novo do servidor. */
    @Synchronized
    fun refresh() {
        m3u = null
        synchronized(cache) { cache.clear() }
    }

    @Synchronized
    fun clear() {
        account = null
        m3u = null
        synchronized(cache) { cache.clear() }
    }

    private fun acc(): Account = account ?: throw IllegalStateException("Faça login novamente.")

    @Synchronized
    private fun m3uItems(): List<Item> =
        m3u ?: Http.read(acc().m3uUrl) { M3uParser.parse(it) }.also { m3u = it }

    /** Baixa a lista M3U e diz quantos itens ela tem (usado no login). */
    fun loadM3u(): Int = m3uItems().size

    fun categories(kind: String): List<Category> {
        val a = acc()
        val cats = if (a.isXtream) {
            XtreamApi(a).categories(kind)
        } else {
            m3uItems().asSequence()
                .filter { it.kind == kind }
                .map { it.categoryId }
                .distinct()
                .map { Category(it, it) }
                .toList()
        }
        return listOf(Category(Kind.ALL, "Todos")) + cats
    }

    fun items(kind: String, categoryId: String): List<Item> {
        val a = acc()
        val key = "$kind|$categoryId"
        synchronized(cache) { cache[key] }?.let { return it }
        val list = if (a.isXtream) {
            XtreamApi(a).items(kind, categoryId)
        } else {
            m3uItems().filter { it.kind == kind && (categoryId == Kind.ALL || it.categoryId == categoryId) }
        }
        synchronized(cache) { cache[key] = list }
        return list
    }

    fun episodes(seriesId: String): List<Item> = XtreamApi(acc()).episodes(seriesId)

    /** EPG curto (só funciona com Xtream e canais ao vivo). */
    fun nowNext(item: Item): String {
        val a = acc()
        return if (a.isXtream && item.kind == Kind.LIVE) XtreamApi(a).nowNext(item.id) else ""
    }
}
