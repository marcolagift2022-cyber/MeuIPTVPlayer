package com.meuiptv.player

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Teste de velocidade da internet do aparelho.
 * Baixa dados de teste dos servidores da Cloudflare (speed.cloudflare.com) por alguns segundos
 * e mede quantos megabits por segundo chegaram. Também mede o "ping" (tempo de resposta).
 * Chame fora da thread principal.
 */
object SpeedTest {

    private const val PING_URL = "https://speed.cloudflare.com/__down?bytes=0"
    private const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes=50000000"
    private const val DURATION_MS = 9_000L
    private const val CONNECTIONS = 3

    data class Result(val pingMs: Long, val mbps: Double, val serverMs: Long?)

    /** Menor tempo de resposta (em ms) entre algumas tentativas. */
    fun ping(address: String = PING_URL, tries: Int = 3): Long {
        var best = Long.MAX_VALUE
        repeat(tries) {
            val start = System.nanoTime()
            val conn = URL(address).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.useCaches = false
                conn.setRequestProperty("User-Agent", Http.userAgent)
                conn.inputStream.use { it.read(ByteArray(512)) }
                best = minOf(best, (System.nanoTime() - start) / 1_000_000)
            } finally {
                conn.disconnect()
            }
        }
        return best
    }

    /**
     * Mede a velocidade de download. [onProgress] recebe a velocidade parcial (Mbps)
     * mais ou menos a cada meio segundo.
     */
    fun download(onProgress: (Double) -> Unit): Double {
        val bytes = AtomicLong(0)
        val start = System.currentTimeMillis()
        val deadline = start + DURATION_MS
        // Algumas conexões ao mesmo tempo, para usar a internet inteira (como os sites de teste fazem)
        val workers = (1..CONNECTIONS).map {
            thread(isDaemon = true) {
                val buffer = ByteArray(64 * 1024)
                while (System.currentTimeMillis() < deadline) {
                    val conn = try {
                        (URL(DOWN_URL).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            useCaches = false
                            setRequestProperty("User-Agent", Http.userAgent)
                        }
                    } catch (e: Exception) {
                        return@thread
                    }
                    try {
                        conn.inputStream.use { input ->
                            while (System.currentTimeMillis() < deadline) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                bytes.addAndGet(n.toLong())
                            }
                        }
                    } catch (e: Exception) {
                        // conexão caiu: tenta abrir outra enquanto houver tempo
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        }
        while (System.currentTimeMillis() < deadline && workers.any { it.isAlive }) {
            Thread.sleep(500)
            onProgress(mbps(bytes.get(), System.currentTimeMillis() - start))
        }
        workers.forEach { it.join(3_000) }
        val total = bytes.get()
        if (total == 0L) throw java.io.IOException("Não foi possível baixar os dados de teste. Confira a internet.")
        return mbps(total, (System.currentTimeMillis() - start).coerceAtMost(DURATION_MS + 500))
    }

    private fun mbps(bytes: Long, ms: Long): Double =
        if (ms <= 0) 0.0 else bytes * 8.0 / 1_000_000.0 / (ms / 1000.0)

    /** Tempo de resposta do servidor de canais (só contas Xtream). */
    fun serverPing(): Long? {
        val a = Repository.account ?: return null
        if (!a.isXtream || a.server.isBlank()) return null
        return try {
            ping("${a.server}/player_api.php?username=${java.net.URLEncoder.encode(a.username, "UTF-8")}" +
                "&password=${java.net.URLEncoder.encode(a.password, "UTF-8")}", tries = 2)
        } catch (e: Exception) {
            null
        }
    }

    /** Explica o resultado em palavras simples. */
    fun verdict(mbps: Double): String = when {
        mbps >= 25 -> "Ótima! Dá para assistir até em 4K."
        mbps >= 12 -> "Boa! Canais e filmes em Full HD sem travar."
        mbps >= 6 -> "Razoável: HD funciona bem; Full HD pode travar às vezes."
        mbps >= 3 -> "Fraca: só qualidade mais baixa (SD). Pode travar."
        else -> "Muito fraca: os canais vão travar. Confira o Wi-Fi ou o roteador."
    }
}
