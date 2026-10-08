package com.meuiptv.player

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var typeGroup: RadioGroup
    private lateinit var server: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var m3uUrl: EditText
    private lateinit var enter: Button
    private lateinit var progress: ProgressBar
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Já tem login salvo? Vai direto para o menu.
        if (Prefs(this).account() != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)
        typeGroup = findViewById(R.id.type_group)
        server = findViewById(R.id.server)
        username = findViewById(R.id.username)
        password = findViewById(R.id.password)
        m3uUrl = findViewById(R.id.m3u_url)
        enter = findViewById(R.id.enter)
        progress = findViewById(R.id.progress)
        error = findViewById(R.id.error)

        val xtreamFields = findViewById<View>(R.id.xtream_fields)
        val m3uFields = findViewById<View>(R.id.m3u_fields)
        typeGroup.setOnCheckedChangeListener { _, checkedId ->
            val xtream = checkedId == R.id.type_xtream
            xtreamFields.visibility = if (xtream) View.VISIBLE else View.GONE
            m3uFields.visibility = if (xtream) View.GONE else View.VISIBLE
            error.visibility = View.GONE
        }

        enter.setOnClickListener { tryLogin() }
    }

    private fun tryLogin() {
        val xtream = typeGroup.checkedRadioButtonId == R.id.type_xtream
        val account = if (xtream) {
            val s = normalizeUrl(server.text.toString())
            val u = username.text.toString().trim()
            val p = password.text.toString().trim()
            if (s.isEmpty() || u.isEmpty() || p.isEmpty()) {
                showError("Preencha servidor, usuário e senha.")
                return
            }
            Account(Account.TYPE_XTREAM, server = s, username = u, password = p)
        } else {
            val url = normalizeUrl(m3uUrl.text.toString())
            if (url.isEmpty()) {
                showError("Cole o link da lista M3U.")
                return
            }
            Account(Account.TYPE_M3U, m3uUrl = url)
        }

        setBusy(true)
        lifecycleScope.launch {
            val problem = withContext(Dispatchers.IO) {
                try {
                    if (account.isXtream) {
                        XtreamApi(account).login()
                    } else {
                        Repository.init(account)
                        if (Repository.loadM3u() == 0) "A lista está vazia ou não é uma lista M3U." else null
                    }
                } catch (e: Exception) {
                    friendlyError(e)
                }
            }
            setBusy(false)
            if (problem != null) {
                showError(problem)
            } else {
                Prefs(this@LoginActivity).saveAccount(account)
                Repository.init(account)
                goToMain()
            }
        }
    }

    /** Aceita "servidor.com:8080" e completa para "http://servidor.com:8080". */
    private fun normalizeUrl(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return s
        if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
            s = "http://$s"
        }
        return s.trimEnd('/')
    }

    private fun setBusy(busy: Boolean) {
        enter.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) error.visibility = View.GONE
    }

    private fun showError(message: String) {
        error.text = message
        error.visibility = View.VISIBLE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
