package com.meuiptv.player

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela inicial. O cliente digita só usuário e senha: o DNS vem do config.json na nuvem.
 * Se já existe login salvo, conecta sozinho (e troca de DNS se o antigo mudou ou caiu).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var form: View
    private lateinit var status: TextView
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var enter: Button
    private lateinit var progress: ProgressBar
    private lateinit var error: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_login)
        form = findViewById(R.id.form)
        status = findViewById(R.id.status)
        username = findViewById(R.id.username)
        password = findViewById(R.id.password)
        enter = findViewById(R.id.enter)
        progress = findViewById(R.id.progress)
        error = findViewById(R.id.error)

        // Olhinho: mostra/esconde a senha digitada
        val toggle = findViewById<ImageButton>(R.id.toggle_password)
        var passwordVisible = false
        // Espaço à direita para o texto não ficar embaixo do olhinho
        password.setPaddingRelative(
            password.paddingStart, password.paddingTop,
            (60 * resources.displayMetrics.density).toInt(), password.paddingBottom,
        )
        toggle.setOnClickListener {
            passwordVisible = !passwordVisible
            val cursor = password.selectionEnd
            password.transformationMethod =
                if (passwordVisible) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            password.setSelection(cursor.coerceIn(0, password.text.length))
            toggle.setImageResource(if (passwordVisible) R.drawable.ic_eye_off else R.drawable.ic_eye)
            toggle.contentDescription = if (passwordVisible) "Esconder senha" else "Mostrar senha"
        }

        enter.setOnClickListener { login(username.text.toString().trim(), password.text.toString().trim(), auto = false) }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                enter.performClick()
                true
            } else {
                false
            }
        }

        val saved = prefs.account()
        when {
            saved == null -> showForm()
            !saved.isXtream -> goToMain("") // login antigo por lista M3U
            else -> {
                username.setText(saved.username)
                password.setText(saved.password)
                login(saved.username, saved.password, auto = true, lastServer = saved.server)
            }
        }
    }

    private fun login(user: String, pass: String, auto: Boolean, lastServer: String? = null) {
        if (user.isEmpty() || pass.isEmpty()) {
            showError("Preencha usuário e senha.")
            return
        }
        setBusy(true, auto)
        lifecycleScope.launch {
            val (config, result) = withContext(Dispatchers.IO) {
                val config = RemoteConfig.load(this@LoginActivity)
                Settings.userAgent = config.userAgent
                // Tenta primeiro o DNS que funcionou da última vez (se ainda estiver na lista)
                val servers = (listOfNotNull(lastServer?.takeIf { it in config.dns }) + config.dns).distinct()
                config to loginWithServers(user, pass, servers)
            }
            setBusy(false, auto)
            val account = result.account
            if (account != null) {
                prefs.saveAccount(account)
                Settings.expDate = result.expDate
                Repository.init(account)
                goToMain(config.notice)
            } else {
                showForm()
                showError(
                    if (result.wrongPassword) {
                        "Usuário ou senha incorretos, ou assinatura vencida."
                    } else {
                        "${result.error}\nVerifique sua internet e toque em Entrar para tentar de novo."
                    }
                )
            }
        }
    }

    private fun showForm() {
        status.visibility = View.GONE
        form.visibility = View.VISIBLE
        if (username.text.isEmpty()) username.requestFocus() else enter.requestFocus()
    }

    private fun setBusy(busy: Boolean, auto: Boolean) {
        enter.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        if (busy) {
            error.visibility = View.GONE
            if (auto) {
                form.visibility = View.GONE
                status.visibility = View.VISIBLE
            }
        }
    }

    private fun showError(message: String) {
        error.text = message
        error.visibility = View.VISIBLE
    }

    private fun goToMain(notice: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra(EXTRA_NOTICE, notice))
        finish()
    }
}
