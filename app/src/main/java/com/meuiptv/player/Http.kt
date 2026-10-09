package com.meuiptv.player

import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads simples de texto (API do Xtream e listas M3U). */
object Http {

    private const val DEFAULT_USER_AGENT = "MeuIPTVPlayer/1.0 (Linux; Android)"

    /** Pode ser trocado pelo campo "user_agent" do config.json. */
    val userAgent: String get() = Settings.userAgent.ifBlank { DEFAULT_USER_AGENT }

    private fun open(address: String): HttpURLConnection {
        var url = address
        // Segue até 5 redirecionamentos, inclusive de http para https
        repeat(5) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", userAgent)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Redirecionamento inválido do servidor")
                url = URL(URL(url), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException(
                    when (code) {
                        401, 403 -> "Acesso negado pelo servidor ($code). Confira usuário e senha."
                        404 -> "Endereço não encontrado no servidor (404)."
                        else -> "O servidor respondeu com erro $code."
                    }
                )
            }
            return conn
        }
        throw IOException("Redirecionamentos demais")
    }

    fun get(url: String): String {
        val conn = open(url)
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun <T> read(url: String, block: (BufferedReader) -> T): T {
        val conn = open(url)
        try {
            return conn.inputStream.bufferedReader().use(block)
        } finally {
            conn.disconnect()
        }
    }
}
