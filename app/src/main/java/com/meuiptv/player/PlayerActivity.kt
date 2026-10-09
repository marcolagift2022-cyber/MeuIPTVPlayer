package com.meuiptv.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lista que o player vai tocar (canais da categoria, um filme ou os episódios). */
object PlayerQueue {
    var items: List<Item> = emptyList()
    var index: Int = 0
}

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var infoBox: View
    private lateinit var infoName: TextView
    private lateinit var infoEpg: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var items: List<Item> = emptyList()
    private var startIndex = 0
    private var startPosition = 0L
    private var retries = 0
    private var epgJob: Job? = null

    private val hideInfo = Runnable { infoBox.visibility = View.GONE }

    private val currentItem: Item?
        get() = items.getOrNull(player?.currentMediaItemIndex ?: startIndex)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = PlayerQueue.items
        if (items.isEmpty()) {
            finish()
            return
        }
        startIndex = PlayerQueue.index.coerceIn(0, items.size - 1)
        savedInstanceState?.let {
            startIndex = it.getInt("index", startIndex).coerceIn(0, items.size - 1)
            startPosition = it.getLong("position", 0L)
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.player_view)
        infoBox = findViewById(R.id.info_box)
        infoName = findViewById(R.id.info_name)
        infoEpg = findViewById(R.id.info_epg)

        // Quando os controles aparecem (toque na tela / OK no controle), mostra o nome e o EPG
        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.VISIBLE) showInfo()
            }
        )
        hideSystemBars()
    }

    override fun onStart() {
        super.onStart()
        if (items.isNotEmpty()) initPlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        player?.let {
            startIndex = it.currentMediaItemIndex
            startPosition = it.currentPosition
        }
        outState.putInt("index", startIndex)
        outState.putLong("position", startPosition)
    }

    private fun initPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))

        // Se o decoder principal do aparelho falhar, tenta outro sozinho
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        playerView.player = p

        val allLive = items.all { it.kind == Kind.LIVE }
        val position = if (items[startIndex].kind == Kind.LIVE) C.TIME_UNSET else startPosition
        p.setMediaItems(items.map { toMediaItem(it) }, startIndex, position)
        // Canais: ao passar do último volta ao primeiro. Episódios: para no fim da série.
        p.repeatMode = if (allLive) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        // Configuração "próximo episódio automático"
        p.pauseAtEndOfMediaItems = !allLive && !Settings.autoNextEpisode
        p.addListener(listener)
        p.playWhenReady = true
        p.prepare()
        player = p
        retries = 0
        updateControls()
        showInfo()
    }

    /**
     * Canal ao vivo: só ⏮ ⏸ ⏭ (sem voltar/avançar 10s e sem a barra de tempo).
     * Filmes e séries: controles completos.
     */
    private fun updateControls() {
        val live = currentItem?.kind == Kind.LIVE
        playerView.setShowRewindButton(!live)
        playerView.setShowFastForwardButton(!live)
        val visibility = if (live) View.GONE else View.VISIBLE
        for (name in listOf("exo_progress", "exo_time", "exo_position", "exo_duration")) {
            val id = resources.getIdentifier(name, "id", packageName)
            if (id != 0) playerView.findViewById<View>(id)?.visibility = visibility
        }
    }

    private fun releasePlayer() {
        handler.removeCallbacksAndMessages(null)
        epgJob?.cancel()
        player?.let {
            startIndex = it.currentMediaItemIndex
            startPosition = it.currentPosition
            it.removeListener(listener)
            it.release()
        }
        player = null
    }

    private fun toMediaItem(item: Item): MediaItem {
        val url = item.url ?: ""
        val builder = MediaItem.Builder().setUri(url).setMediaId(item.key)
        if (url.contains(".m3u8", ignoreCase = true)) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        return builder.build()
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            retries = 0
            updateControls()
            showInfo()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) retries = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            // Conexões de IPTV caem com frequência: tenta reconectar sozinho algumas vezes
            if (retries < 3) {
                retries++
                toast("Reconectando... ($retries/3)")
                handler.postDelayed({
                    player?.let {
                        if (currentItem?.kind == Kind.LIVE) it.seekToDefaultPosition()
                        it.prepare()
                    }
                }, 2_000)
            } else {
                toast("Não foi possível reproduzir este conteúdo (${error.errorCodeName}).")
            }
        }
    }

    /** Mostra por alguns segundos o nome do canal/filme e, nos canais, a programação. */
    private fun showInfo() {
        val p = player ?: return
        val index = p.currentMediaItemIndex
        val item = items.getOrNull(index) ?: return

        infoName.text = if (item.kind == Kind.LIVE && items.size > 1) "${index + 1}. ${item.name}" else item.name
        infoEpg.visibility = View.GONE
        infoBox.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        handler.postDelayed(hideInfo, 6_000)

        epgJob?.cancel()
        if (item.kind != Kind.LIVE) return
        epgJob = lifecycleScope.launch {
            val epg = try {
                withContext(Dispatchers.IO) { Repository.nowNext(item) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ""
            }
            if (epg.isNotBlank()) {
                infoEpg.text = epg
                infoEpg.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Troca de canal pelo controle remoto: setas cima/baixo ou CH+/CH- (com os controles escondidos).
     * No celular, use os botões ⏮ ⏭ que aparecem ao tocar na tela.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val p = player
        if (p != null && currentItem?.kind == Kind.LIVE && items.size > 1 && !playerView.isControllerFullyVisible) {
            val direction = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> 1
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> -1
                else -> 0
            }
            if (direction != 0) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (direction > 0) p.seekToNextMediaItem() else p.seekToPreviousMediaItem()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
