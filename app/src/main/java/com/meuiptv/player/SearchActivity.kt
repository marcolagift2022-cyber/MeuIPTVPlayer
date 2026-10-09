package com.meuiptv.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Busca geral: procura ao mesmo tempo em canais, filmes e séries. */
class SearchActivity : AppCompatActivity() {

    private val kinds = listOf(Kind.LIVE to "Canais", Kind.MOVIE to "Filmes", Kind.SERIES to "Séries")

    private lateinit var prefs: Prefs
    private lateinit var search: EditText
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var tabAdapter: TextAdapter
    private lateinit var itemAdapter: ItemAdapter
    private lateinit var grid: GridLayoutManager

    /** Catálogo completo de cada tipo (carregado uma vez). */
    private var catalog: Map<String, List<Item>>? = null
    private var results: Map<String, List<Item>> = emptyMap()
    private var tab = 0
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        prefs = Prefs(this)
        search = findViewById(R.id.search)
        progress = findViewById(R.id.progress)
        message = findViewById(R.id.message)

        tabAdapter = TextAdapter(wide = false) { selectTab(it) }
        findViewById<RecyclerView>(R.id.tabs).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = tabAdapter
        }

        itemAdapter = ItemAdapter(
            onClick = { item, position -> open(item, position) },
            onToggleFavorite = { item ->
                val added = prefs.toggleFavorite(item)
                toast(if (added) "Adicionado aos favoritos" else "Removido dos favoritos")
                itemAdapter.favoriteKeys = prefs.favoriteKeys()
            },
        )
        grid = GridLayoutManager(this, spansFor(Kind.LIVE))
        findViewById<RecyclerView>(R.id.items).apply {
            layoutManager = grid
            adapter = itemAdapter
        }

        updateTabs()
        message.text = "Digite pelo menos 2 letras para buscar."
        search.doAfterTextChanged {
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(350) // espera parar de digitar
                runSearch()
            }
        }
        search.requestFocus()
        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        if (::itemAdapter.isInitialized) itemAdapter.favoriteKeys = prefs.favoriteKeys()
    }

    /** Baixa (ou pega do cache) todos os canais, filmes e séries. */
    private fun loadCatalog() {
        progress.visibility = View.VISIBLE
        message.text = "Carregando o catálogo..."
        lifecycleScope.launch {
            catalog = withContext(Dispatchers.IO) {
                kinds.associate { (kind, _) ->
                    val list = try {
                        val all = Repository.items(kind, Kind.ALL)
                        if (adultLocked()) {
                            val adult = Repository.adultCategoryIds(kind)
                            all.filterNot { it.categoryId in adult }
                        } else {
                            all
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    kind to list
                }
            }
            progress.visibility = View.GONE
            runSearch()
        }
    }

    private fun runSearch() {
        val data = catalog ?: return
        val query = search.text.toString().trim()
        if (query.length < 2) {
            results = emptyMap()
            updateTabs()
            showResults()
            message.text = "Digite pelo menos 2 letras para buscar."
            message.visibility = View.VISIBLE
            return
        }
        results = kinds.associate { (kind, _) ->
            kind to (data[kind] ?: emptyList()).filter { it.name.contains(query, ignoreCase = true) }.take(300)
        }
        // Se a aba atual não tem nada, pula para a primeira que tiver resultado
        if (results[kinds[tab].first].isNullOrEmpty()) {
            val firstWithResults = kinds.indexOfFirst { !results[it.first].isNullOrEmpty() }
            if (firstWithResults >= 0) tab = firstWithResults
        }
        updateTabs()
        showResults()
    }

    private fun selectTab(index: Int) {
        tab = index
        updateTabs()
        showResults()
    }

    private fun updateTabs() {
        tabAdapter.labels = kinds.map { (kind, label) ->
            val n = results[kind]?.size ?: 0
            if (results.isEmpty()) label else "$label ($n)"
        }
        tabAdapter.selected = tab
    }

    private fun showResults() {
        val kind = kinds[tab].first
        val list = results[kind] ?: emptyList()
        grid.spanCount = spansFor(kind)
        itemAdapter.favoriteKeys = prefs.favoriteKeys()
        itemAdapter.items = list
        if (results.isNotEmpty()) {
            message.text = "Nada encontrado em ${kinds[tab].second.lowercase()}."
            message.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun spansFor(kind: String): Int {
        val widthDp = resources.configuration.screenWidthDp - 60
        val card = if (kind == Kind.LIVE) 130 else 120
        return max(2, widthDp / card)
    }

    private fun open(item: Item, position: Int) {
        if (item.url == null) {
            startActivity(
                Intent(this, EpisodesActivity::class.java)
                    .putExtra(EXTRA_ID, item.id)
                    .putExtra(EXTRA_NAME, item.name)
            )
            return
        }
        if (item.kind == Kind.LIVE) {
            val channels = itemAdapter.items.filter { it.url != null }
            PlayerQueue.items = channels
            PlayerQueue.index = channels.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
        } else {
            PlayerQueue.items = listOf(item)
            PlayerQueue.index = 0
        }
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}
