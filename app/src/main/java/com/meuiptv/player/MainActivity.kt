package com.meuiptv.player

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Menu principal: TV ao vivo, Filmes, Séries e Favoritos. */
class MainActivity : AppCompatActivity() {

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

        findViewById<TextView>(R.id.account).text =
            if (account.isXtream) "${account.username} · ${account.server}" else "Lista M3U"

        val live = findViewById<Button>(R.id.btn_live)
        live.setOnClickListener { open(Kind.LIVE) }
        findViewById<Button>(R.id.btn_movies).setOnClickListener { open(Kind.MOVIE) }
        findViewById<Button>(R.id.btn_series).setOnClickListener { open(Kind.SERIES) }
        findViewById<Button>(R.id.btn_favorites).setOnClickListener { open(Kind.FAV) }
        findViewById<Button>(R.id.logout).setOnClickListener { confirmLogout() }

        live.requestFocus() // foco inicial para quem usa controle remoto
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
                Repository.clear()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
