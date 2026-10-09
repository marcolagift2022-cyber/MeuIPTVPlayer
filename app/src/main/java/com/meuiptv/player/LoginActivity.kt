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
 * Tela inicial.
 *  - Versão "direto": o cliente digita só usuário e senha; o DNS vem do config.json na nuvem.
 *    Se já existe login salvo, conecta sozinho (e troca de DNS se o antigo mudou ou caiu).
 *  - Versão "play" (Google Play): reprodutor genérico. O cliente escolhe Xtream Codes
 *    (servidor, usuário e senha) ou Lista M3U (link) e informa os dados do próprio provedor.
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
    private lateinit var server: EditText
    private lateinit var m3u: EditText
    private lateinit var tabXtream: Button
    private lateinit var tabM3u: Button
    private var mode = Account.TYPE_XTREAM

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
        server = findViewById(R.id.server)
        m3u = findViewById(R.id.m3u)
        tabXtream = findViewById(R.id.tab_xtream)
        tabM3u = findViewById(R.id.tab_m3u)

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

        enter.setOnClickListener {
            if (BuildConfig.PLAY_STORE) {
                playLogin(auto = false)
            } else {
                login(username.text.toString().trim(), password.text.toString().trim(), auto = false)
            }
        }
        m3u.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                enter.performClick()
                true
            } else {
                false
            }
        }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                enter.performClick()
                true
            } else {
                false
            }
        }

        val saved = prefs.account()
        if (BuildConfig.PLAY_STORE) {
            setupPlay(saved)
            return
        }
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

    // ------------------------------------------------------------ versão Google Play

    private fun setupPlay(saved: Account?) {
        findViewById<TextView>(R.id.subtitle).text = "Entre com a conta ou a lista do seu provedor."
        findViewById<View>(R.id.tabs).visibility = View.VISIBLE
        findViewById<View>(R.id.disclaimer).visibility = View.VISIBLE
        tabXtream.setOnClickListener { setMode(Account.TYPE_XTREAM) }
        tabM3u.setOnClickListener { setMode(Account.TYPE_M3U) }

        if (saved != null) {
            server.setText(saved.server)
            username.setText(saved.username)
            password.setText(saved.password)
            m3u.setText(saved.m3uUrl)
        }
        setMode(saved?.type ?: Account.TYPE_XTREAM)
        when {
            saved == null -> showForm()
            !saved.isXtream -> goToMain("")
            else -> playLogin(auto = true)
        }
    }

    private fun setMode(newMode: String) {
        mode = newMode
        val xtream = mode == Account.TYPE_XTREAM
        server.visibility = if (xtream) View.VISIBLE else View.GONE
        username.visibility = if (xtream) View.VISIBLE else View.GONE
        findViewById<View>(R.id.password_box).visibility = if (xtream) View.VISIBLE else View.GONE
        m3u.visibility = if (xtream) View.GONE else View.VISIBLE
        val on = getColor(R.color.accent)
        val off = getColor(R.color.text_dim)
        tabXtream.setTextColor(if (xtream) on else off)
        tabM3u.setTextColor(if (xtream) off else on)
        tabXtream.setTypeface(null, if (xtream) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        tabM3u.setTypeface(null, if (xtream) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)
        error.visibility = View.GONE
    }

    private fun playLogin(auto: Boolean) {
        if (mode == Account.TYPE_XTREAM) {
            val address = normalizeUrl(server.text.toString())
            val user = username.text.toString().trim()
            val pass = password.text.toString().trim()
            if (address.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                showError("Preencha servidor, usuário e senha.")
                return
            }
            setBusy(true, auto)
            lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) { loginWithServers(user, pass, listOf(address)) }
                setBusy(false, auto)
                val account = result.account
                if (account != null) {
                    prefs.saveAccount(account)
                    Settings.expDate = result.expDate
                    Repository.init(account)
                    goToMain("")
                } else {
                    showForm()
                    showError(
                        if (result.wrongPassword) {
                            "Usuário ou senha incorretos, ou assinatura vencida."
                        } else {
                            "${result.error}\nConfira o endereço do servidor e a sua internet."
                        }
                    )
                }
            }
        } else {
            val link = normalizeUrl(m3u.text.toString())
            if (link.isEmpty()) {
                showError("Digite o link da lista M3U.")
                return
            }
            val account = Account(Account.TYPE_M3U, m3uUrl = link)
            setBusy(true, auto)
            lifecycleScope.launch {
                val problem = withContext(Dispatchers.IO) {
                    try {
                        Repository.init(account)
                        Repository.refresh()
                        if (Repository.loadM3u() == 0) "A lista não tem nenhum canal ou vídeo." else null
                    } catch (e: Exception) {
                        friendlyError(e)
                    }
                }
                setBusy(false, auto)
                if (problem == null) {
                    prefs.saveAccount(account)
                    Settings.expDate = 0L
                    goToMain("")
                } else {
                    Repository.clear()
                    showForm()
                    showError(problem)
                }
            }
        }
    }

    // ------------------------------------------------------------ comum

    private fun showForm() {
        status.visibility = View.GONE
        form.visibility = View.VISIBLE
        when {
            BuildConfig.PLAY_STORE && mode == Account.TYPE_XTREAM && server.text.isEmpty() -> server.requestFocus()
            BuildConfig.PLAY_STORE && mode == Account.TYPE_M3U && m3u.text.isEmpty() -> m3u.requestFocus()
            username.text.isEmpty() && username.visibility == View.VISIBLE -> username.requestFocus()
            else -> enter.requestFocus()
        }
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
