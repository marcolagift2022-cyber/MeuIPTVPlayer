package com.meuiptv.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tela de detalhes do filme ou série: capa, arte de fundo, nota, ano, sinopse
 * e o botão "Assistir". Na série mostra também as temporadas e os episódios.
 */
class DetailsActivity : AppCompatActivity() {

    private lateinit var item: Item
    private lateinit var prefs: Prefs
    private lateinit var playButton: Button
    private lateinit var restartButton: Button
    private lateinit var favoriteButton: Button

    private var episodes: List<Item> = emptyList()
    private var seasons: List<String> = emptyList()
    private var currentSeason = 0
    private lateinit var seasonAdapter: TextAdapter
    private lateinit var episodeAdapter: TextAdapter

    private val isSeries get() = item.kind == Kind.SERIES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_details)
        val json = intent.getStringExtra(EXTRA_ITEM) ?: run { finish(); return }
        item = Item.fromJson(org.json.JSONObject(json))
        prefs = Prefs(this)

        playButton = findViewById(R.id.play)
        restartButton = findViewById(R.id.restart)
        favoriteButton = findViewById(R.id.favorite)
        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.poster).clipToOutline = true

        // Mostra na hora o que já temos (nome e capa) enquanto busca o resto no servidor
        findViewById<TextView>(R.id.title).text = item.name
        loadPoster(item.icon)
        loadBackdrop(item.icon)

        playButton.setOnClickListener { play(fromStart = false) }
        restartButton.setOnClickListener { play(fromStart = true) }
        favoriteButton.setOnClickListener {
            val added = prefs.toggleFavorite(item)
            toast(if (added) "Adicionado aos favoritos" else "Removido dos favoritos")
            updateFavorite()
        }
        updateFavorite()

        if (isSeries) setupSeries() else updatePlayButtons()
        playButton.requestFocus()
        load()
    }

    override fun onResume() {
        super.onResume()
        // Voltando do player: atualiza "Continuar" e o episódio onde parou
        if (::item.isInitialized) {
            updatePlayButtons()
            if (isSeries && episodes.isNotEmpty()) showSeason(currentSeason)
        }
    }

    private fun load() {
        val progress = findViewById<ProgressBar>(R.id.progress)
        lifecycleScope.launch {
            try {
                val details = withContext(Dispatchers.IO) {
                    if (isSeries) {
                        val (d, eps) = Repository.seriesDetails(item)
                        episodes = eps
                        d
                    } else {
                        Repository.movieDetails(item)
                    }
                }
                showDetails(details)
                if (isSeries) showEpisodes()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isSeries) {
                    findViewById<TextView>(R.id.plot).apply {
                        text = friendlyError(e)
                        visibility = View.VISIBLE
                    }
                }
                // Filme: mesmo sem sinopse dá para assistir normalmente
            } finally {
                progress.visibility = View.GONE
            }
        }
    }

    private fun showDetails(d: Details) {
        findViewById<TextView>(R.id.title).text = d.title
        if (d.poster != item.icon) loadPoster(d.poster)
        loadBackdrop(d.backdrop)

        val meta = listOfNotNull(
            d.year,
            d.genre.takeIf { it.isNotBlank() },
            d.duration.takeIf { it.isNotBlank() }?.let { formatDuration(it) },
        ).joinToString("  ·  ")
        setText(R.id.meta, meta)

        setText(R.id.rating, d.rating?.let { stars(it) + "   " + String.format(Locale.US, "%.1f", it) })
        setText(R.id.added, d.added?.let {
            val date = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(it * 1000))
            if (isSeries) "Atualizada em $date" else "Adicionado em $date"
        })
        setText(R.id.plot, d.plot.takeIf { it.isNotBlank() } ?: "Sinopse não disponível.")
        val credits = listOfNotNull(
            d.director.takeIf { it.isNotBlank() }?.let { "Direção: $it" },
            d.cast.takeIf { it.isNotBlank() }?.let { "Elenco: $it" },
        ).joinToString("\n")
        setText(R.id.credits, credits)
    }

    // ---------- Séries: temporadas e episódios ----------

    private fun setupSeries() {
        playButton.text = "▶  Assistir"
        findViewById<View>(R.id.series_box).visibility = View.VISIBLE
        seasonAdapter = TextAdapter(wide = false) { showSeason(it) }
        findViewById<RecyclerView>(R.id.seasons).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = seasonAdapter
        }
        episodeAdapter = TextAdapter(wide = true) { index ->
            val ep = seasonEpisodes().getOrNull(index) ?: return@TextAdapter
            playEpisode(episodes.indexOf(ep), fromStart = false)
        }
        findViewById<RecyclerView>(R.id.episodes).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = episodeAdapter
        }
    }

    private fun showEpisodes() {
        if (episodes.isEmpty()) {
            setText(R.id.plot, (findViewById<TextView>(R.id.plot).text.toString() + "\n\nNenhum episódio encontrado.").trim())
            playButton.isEnabled = false
            return
        }
        seasons = episodes.map { it.categoryId }.distinct()
        seasonAdapter.labels = seasons.map { "Temporada $it" }
        // Abre na temporada do episódio onde o cliente parou (ou na primeira)
        val last = lastWatchedIndex()
        currentSeason = if (last >= 0) seasons.indexOf(episodes[last].categoryId).coerceAtLeast(0) else 0
        showSeason(currentSeason)
        updatePlayButtons()
    }

    private fun seasonEpisodes(): List<Item> =
        seasons.getOrNull(currentSeason)?.let { s -> episodes.filter { it.categoryId == s } } ?: emptyList()

    private fun showSeason(index: Int) {
        if (index !in seasons.indices) return
        currentSeason = index
        seasonAdapter.selected = index
        episodeAdapter.labels = seasonEpisodes().map { ep ->
            val name = ep.name.substringAfter("· ", ep.name)
            val saved = ResumeStore.get(this, ep.key)
            if (saved >= ResumeStore.MIN_POSITION_MS) "$name   (parou em ${ResumeStore.format(saved)})" else name
        }
    }

    /** Último episódio que o cliente começou a ver (pela ordem da lista), ou -1. */
    private fun lastWatchedIndex(): Int =
        episodes.indexOfLast { ResumeStore.get(this, it.key) >= ResumeStore.MIN_POSITION_MS }

    // ---------- Assistir ----------

    private fun updatePlayButtons() {
        if (isSeries) {
            val last = lastWatchedIndex()
            if (last >= 0) {
                val ep = episodes[last]
                val code = ep.name.substringBefore(" —")
                playButton.text = "▶  Continuar $code"
            } else {
                playButton.text = "▶  Assistir"
            }
            restartButton.visibility = View.GONE
            return
        }
        val saved = ResumeStore.get(this, item.key)
        if (saved >= ResumeStore.MIN_POSITION_MS) {
            playButton.text = "▶  Continuar (${ResumeStore.format(saved)})"
            restartButton.visibility = View.VISIBLE
        } else {
            playButton.text = "▶  Assistir"
            restartButton.visibility = View.GONE
        }
    }

    private fun play(fromStart: Boolean) {
        if (isSeries) {
            if (episodes.isEmpty()) {
                toast("Carregando episódios...")
                return
            }
            playEpisode(lastWatchedIndex().coerceAtLeast(0), fromStart)
            return
        }
        if (fromStart) ResumeStore.clear(this, item.key)
        PlayerQueue.items = listOf(item)
        PlayerQueue.index = 0
        // O cliente já escolheu aqui: o player não pergunta de novo
        PlayerQueue.autoResume = !fromStart
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    /** Toca a série a partir deste episódio (os próximos seguem sozinhos). */
    private fun playEpisode(index: Int, fromStart: Boolean) {
        if (index !in episodes.indices) return
        if (fromStart) ResumeStore.clear(this, episodes[index].key)
        PlayerQueue.items = episodes
        PlayerQueue.index = index
        PlayerQueue.autoResume = false
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    // ---------- Ajudantes ----------

    private fun updateFavorite() {
        val fav = item.key in prefs.favoriteKeys()
        favoriteButton.text = if (fav) "★  Favorito" else "☆  Favoritar"
    }

    private fun loadPoster(url: String?) {
        findViewById<ImageView>(R.id.poster).load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
            fallback(R.drawable.ic_placeholder)
        }
    }

    private fun loadBackdrop(url: String?) {
        if (url.isNullOrBlank()) return
        findViewById<ImageView>(R.id.backdrop).load(url) { crossfade(true) }
    }

    private fun setText(id: Int, text: String?) {
        val v = findViewById<TextView>(id)
        v.text = text ?: ""
        v.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** "★★★☆☆" a partir de uma nota de 0 a 10. */
    private fun stars(rating: Double): String {
        val full = (rating.coerceIn(0.0, 10.0) / 2).roundToInt()
        return "★".repeat(full) + "☆".repeat(5 - full)
    }

    /** "01:52:10" ou "112" (minutos) → "1h 52min". */
    private fun formatDuration(raw: String): String {
        val parts = raw.trim().split(":").mapNotNull { it.trim().toIntOrNull() }
        val minutes = when {
            parts.size == 3 -> parts[0] * 60 + parts[1]
            parts.size == 2 -> parts[0] * 60 + parts[1]
            parts.size == 1 && !raw.contains(Regex("[a-zA-Z]")) -> parts[0]
            else -> return raw
        }
        if (minutes <= 0) return raw
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}min" else "${minutes}min"
    }

    companion object {
        const val EXTRA_ITEM = "item"

        /**
         * Abre um item da grade: filme ou série do Xtream vai para a tela de detalhes.
         * Retorna false se não for o caso (canal, lista M3U), e quem chamou toca direto.
         */
        fun openIfSupported(activity: Activity, item: Item): Boolean {
            if (!Repository.hasDetails(item)) return false
            activity.startActivity(
                Intent(activity, DetailsActivity::class.java)
                    .putExtra(EXTRA_ITEM, item.toJson().toString())
            )
            return true
        }
    }
}
