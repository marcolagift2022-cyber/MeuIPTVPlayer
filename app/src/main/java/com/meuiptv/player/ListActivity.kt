package com.meuiptv.player

import android.content.Intent
import android.content.res.Configuration
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Tela de categorias + grade de canais/filmes/séries (e também Favoritos). */
class ListActivity : AppCompatActivity() {

    private lateinit var kind: String
    private lateinit var prefs: Prefs
    private lateinit var catAdapter: TextAdapter
    private lateinit var itemAdapter: ItemAdapter
    private lateinit var progress: ProgressBar
    private lateinit var message: TextView
    private lateinit var search: EditText

    private var categories: List<Category> = emptyList()
    private var allItems: List<Item> = emptyList()
    private var shown: List<Item> = emptyList()
    private var currentCat = -1
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        kind = intent.getStringExtra(EXTRA_KIND) ?: Kind.LIVE
        prefs = Prefs(this)

        findViewById<TextView>(R.id.title).text = when (kind) {
            Kind.LIVE -> "TV ao vivo"
            Kind.MOVIE -> "Filmes"
            Kind.SERIES -> "Séries"
            else -> "Favoritos"
        }
        progress = findViewById(R.id.progress)
        message = findViewById(R.id.message)
        search = findViewById(R.id.search)

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        catAdapter = TextAdapter(wide = landscape) { selectCategory(it) }
        findViewById<RecyclerView>(R.id.categories).apply {
            layoutManager = LinearLayoutManager(
                context,
                if (landscape) RecyclerView.VERTICAL else RecyclerView.HORIZONTAL,
                false,
            )
            adapter = catAdapter
        }

        itemAdapter = ItemAdapter(
            onClick = { item, position -> open(item, position) },
            onToggleFavorite = { item -> toggleFavorite(item) },
        )
        val density = resources.displayMetrics.density
        val categoryColumnDp = resources.getDimension(R.dimen.category_column_width) / density
        val gridWidthDp = resources.configuration.screenWidthDp - (if (landscape) categoryColumnDp + 60f else 24f)
        val cardWidthDp = if (kind == Kind.LIVE) 130 else 120
        findViewById<RecyclerView>(R.id.items).apply {
            layoutManager = GridLayoutManager(context, max(2, (gridWidthDp / cardWidthDp).toInt()))
            adapter = itemAdapter
        }

        search.doAfterTextChanged { applyFilter() }

        // Botão atualizar: baixa de novo as categorias e o conteúdo do servidor
        findViewById<View>(R.id.refresh).setOnClickListener {
            val keep = categories.getOrNull(currentCat)?.id
            Repository.refresh()
            toast("Atualizando lista...")
            loadCategories(keep)
        }

        loadCategories()
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar do player, atualiza as estrelas (e a lista, na tela de Favoritos)
        if (currentCat < 0) return
        if (kind == Kind.FAV) selectCategory(currentCat) else itemAdapter.favoriteKeys = prefs.favoriteKeys()
    }

    private fun loadCategories(keepCategoryId: String? = null) {
        if (kind == Kind.FAV) {
            categories = listOf(
                Category(Kind.LIVE, "TV ao vivo"),
                Category(Kind.MOVIE, "Filmes"),
                Category(Kind.SERIES, "Séries"),
            )
            showCategories(keepCategoryId)
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                categories = withContext(Dispatchers.IO) { Repository.categories(kind) }
                showCategories(keepCategoryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoading(false)
                showMessage(friendlyError(e))
            }
        }
    }

    private fun showCategories(keepCategoryId: String? = null) {
        catAdapter.labels = categories.map { it.name }
        if (categories.isEmpty()) {
            setLoading(false)
            showMessage("Nenhuma categoria encontrada.")
            return
        }
        // Começa na primeira categoria real (a posição 0 é "Todos", que pode ser enorme)
        val kept = categories.indexOfFirst { it.id == keepCategoryId }
        selectCategory(
            when {
                kept >= 0 -> kept
                kind != Kind.FAV && categories.size > 1 -> 1
                else -> 0
            }
        )
    }

    private fun selectCategory(index: Int) {
        if (index !in categories.indices) return
        currentCat = index
        catAdapter.selected = index
        val category = categories[index]
        loadJob?.cancel()

        if (kind == Kind.FAV) {
            setItems(prefs.favorites().filter { it.kind == category.id })
            return
        }
        setLoading(true)
        loadJob = lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { Repository.items(kind, category.id) }
                setItems(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setLoading(false)
                itemAdapter.items = emptyList()
                showMessage(friendlyError(e))
            }
        }
    }

    private fun setItems(list: List<Item>) {
        allItems = list
        setLoading(false)
        itemAdapter.favoriteKeys = prefs.favoriteKeys()
        applyFilter()
    }

    private fun applyFilter() {
        val query = search.text.toString().trim()
        shown = if (query.isEmpty()) allItems else allItems.filter { it.name.contains(query, ignoreCase = true) }
        itemAdapter.items = shown
        if (progress.visibility == View.VISIBLE) return
        showMessage(
            when {
                shown.isNotEmpty() -> null
                kind == Kind.FAV && query.isEmpty() ->
                    "Nenhum favorito aqui ainda.\nToque e segure (ou aperte Menu no controle) em um item para favoritar."
                else -> "Nada encontrado."
            }
        )
    }

    private fun open(item: Item, position: Int) {
        if (item.url == null) {
            // Série do Xtream: abre a lista de episódios
            startActivity(
                Intent(this, EpisodesActivity::class.java)
                    .putExtra(EXTRA_ID, item.id)
                    .putExtra(EXTRA_NAME, item.name)
            )
            return
        }
        if (item.kind == Kind.LIVE) {
            // Passa a lista inteira para poder trocar de canal dentro do player
            val channels = shown.filter { it.url != null }
            PlayerQueue.items = channels
            PlayerQueue.index = channels.indexOfFirst { it.key == item.key }.coerceAtLeast(0)
        } else {
            PlayerQueue.items = listOf(item)
            PlayerQueue.index = 0
        }
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun toggleFavorite(item: Item) {
        val added = prefs.toggleFavorite(item)
        toast(if (added) "Adicionado aos favoritos" else "Removido dos favoritos")
        if (kind == Kind.FAV) selectCategory(currentCat) else itemAdapter.favoriteKeys = prefs.favoriteKeys()
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) message.visibility = View.GONE
    }

    private fun showMessage(text: String?) {
        message.text = text ?: ""
        message.visibility = if (text == null) View.GONE else View.VISIBLE
    }
}
