package com.meuiptv.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lista de episódios de uma série (Xtream). Ao terminar um, o próximo começa sozinho. */
class EpisodesActivity : AppCompatActivity() {

    private var episodes: List<Item> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_episodes)

        val seriesId = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        findViewById<TextView>(R.id.title).text = intent.getStringExtra(EXTRA_NAME) ?: "Episódios"
        val progress = findViewById<ProgressBar>(R.id.progress)
        val message = findViewById<TextView>(R.id.message)

        val adapter = TextAdapter(wide = true) { index ->
            PlayerQueue.items = episodes
            PlayerQueue.index = index
            startActivity(Intent(this, PlayerActivity::class.java))
        }
        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        lifecycleScope.launch {
            try {
                episodes = withContext(Dispatchers.IO) { Repository.episodes(seriesId) }
                adapter.labels = episodes.map { it.name }
                if (episodes.isEmpty()) {
                    message.text = "Nenhum episódio encontrado."
                    message.visibility = View.VISIBLE
                } else {
                    list.post { list.getChildAt(0)?.requestFocus() }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.text = friendlyError(e)
                message.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }
}
