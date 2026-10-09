package com.meuiptv.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Menu principal: TV ao vivo, Filmes, Séries, Favoritos + Buscar, Ajustes, Atualizar e Sair. */
class MainActivity : AppCompatActivity() {

    private val ptBR = Locale("pt", "BR")
    private val clockFormat = SimpleDateFormat("HH:mm  ·  dd/MM/yyyy", ptBR)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView

    private val tick = object : Runnable {
        override fun run() {
            clock.text = clockFormat.format(Date())
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val account = Prefs(this).account()
        if (account == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        Repository.init(account)
        setContentView(R.layout.activity_main)

        clock = findViewById(R.id.clock)
        findViewById<TextView>(R.id.account).text =
            if (account.isXtream) "Usuário: ${account.username}" else "Lista M3U"
        showExpiry(findViewById(R.id.expiry))

        val notice = intent.getStringExtra(EXTRA_NOTICE).orEmpty()
        findViewById<TextView>(R.id.notice).apply {
            text = notice
            visibility = if (notice.isBlank()) View.GONE else View.VISIBLE
        }

        val live = findViewById<Button>(R.id.btn_live)
        live.setOnClickListener { open(Kind.LIVE) }
        findViewById<Button>(R.id.btn_movies).setOnClickListener { open(Kind.MOVIE) }
        findViewById<Button>(R.id.btn_series).setOnClickListener { open(Kind.SERIES) }
        findViewById<Button>(R.id.btn_favorites).setOnClickListener { open(Kind.FAV) }

        findViewById<Button>(R.id.btn_search).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            Repository.refresh()
            toast("Lista atualizada! Os conteúdos novos já vão aparecer.")
        }
        findViewById<Button>(R.id.logout).setOnClickListener { confirmLogout() }

        live.requestFocus() // foco inicial para quem usa controle remoto
    }

    override fun onStart() {
        super.onStart()
        if (::clock.isInitialized) tick.run()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(tick)
    }

    /** "Vencimento: 16/07/2027 (281 dias)" — em vermelho quando faltam poucos dias. */
    private fun showExpiry(view: TextView) {
        val exp = Settings.expDate
        if (exp <= 0L) {
            view.visibility = View.GONE
            return
        }
        val date = Date(exp * 1000)
        val days = TimeUnit.MILLISECONDS.toDays(date.time - System.currentTimeMillis())
        val dateText = SimpleDateFormat("dd/MM/yyyy", ptBR).format(date)
        view.text = when {
            days < 0 -> "Assinatura vencida em $dateText"
            days == 0L -> "Vence hoje ($dateText)"
            days == 1L -> "Vencimento: $dateText (amanhã)"
            else -> "Vencimento: $dateText ($days dias)"
        }
        if (days <= 5) view.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.error))
        view.visibility = View.VISIBLE
    }

    private fun open(kind: String) {
        startActivity(Intent(this, ListActivity::class.java).putExtra(EXTRA_KIND, kind))
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Sair da conta?")
            .setMessage("Você vai precisar digitar os dados de acesso de novo. Os favoritos continuam salvos.")
            .setPositiveButton("Sair") { _, _ ->
                Prefs(this).logout()
                Settings.expDate = 0L
                Repository.clear()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
