package com.meuiptv.player

import android.content.Context
import org.json.JSONException
import org.json.JSONObject

/**
 * Configuração que fica na nuvem (arquivo config.json no GitHub).
 * Para trocar o DNS de todos os clientes, basta editar esse arquivo pelo site do GitHub.
 */
object RemoteConfig {

    /** Endereço do arquivo de configuração (repositório público no GitHub). */
    const val CONFIG_URL =
        "https://raw.githubusercontent.com/marcolagift2022-cyber/MeuIPTVPlayer/main/config.json"

    /** Usado só se o app nunca conseguiu baixar a configuração. */
    private val DEFAULT_DNS = listOf("http://xbrtoprev.site")

    data class Config(val dns: List<String>, val notice: String)

    /** Baixa a configuração. Sem internet, usa a última que funcionou. Chame fora da thread principal. */
    fun load(context: Context): Config {
        val sp = context.getSharedPreferences("meu_iptv", Context.MODE_PRIVATE)
        val fresh = try {
            // O "?t=" evita receber uma versão antiga guardada em cache
            Http.get("$CONFIG_URL?t=${System.currentTimeMillis()}").also {
                parse(it)
                sp.edit().putString("remote_config", it).apply()
            }
        } catch (e: Exception) {
            null
        }
        val text = fresh ?: sp.getString("remote_config", null)
        return text?.let { runCatching { parse(it) }.getOrNull() } ?: Config(DEFAULT_DNS, "")
    }

    private fun parse(text: String): Config {
        val o = JSONObject(text)
        val arr = o.optJSONArray("dns")
        val list = ArrayList<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = normalizeUrl(arr.optString(i))
                if (s.isNotEmpty()) list += s
            }
        }
        if (list.isEmpty()) throw JSONException("config.json sem nenhum DNS")
        return Config(list, if (o.isNull("aviso")) "" else o.optString("aviso").trim())
    }
}

/** Resultado de uma tentativa de login. */
data class LoginResult(val account: Account?, val error: String?, val wrongPassword: Boolean)

/** Tenta o login em cada DNS da lista, na ordem, até um aceitar. */
fun loginWithServers(user: String, pass: String, servers: List<String>): LoginResult {
    var authError: String? = null
    var networkError: String? = null
    for (server in servers) {
        val account = Account(Account.TYPE_XTREAM, server = server, username = user, password = pass)
        try {
            val err = XtreamApi(account).login() ?: return LoginResult(account, null, false)
            if (authError == null) authError = err
        } catch (e: Exception) {
            if (networkError == null) networkError = friendlyError(e)
        }
    }
    return if (authError != null) {
        LoginResult(null, authError, true)
    } else {
        LoginResult(null, networkError ?: "Não foi possível conectar ao servidor.", false)
    }
}
