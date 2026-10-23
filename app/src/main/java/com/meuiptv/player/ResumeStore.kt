package com.meuiptv.player

import android.content.Context

/**
 * Guarda onde o cliente parou em cada filme/episódio ("continuar de onde parou").
 * Fica só no aparelho. Guarda no máximo os 300 mais recentes.
 */
object ResumeStore {

    private const val PREFS = "continuar_assistindo"
    private const val MAX_ITEMS = 300

    /** Menos que isso não vale a pena perguntar (o cliente mal começou). */
    const val MIN_POSITION_MS = 30_000L

    private fun sp(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Posição salva (ms) ou 0 se não houver. */
    fun get(context: Context, key: String): Long {
        val value = sp(context).getString(key, null) ?: return 0L
        return value.substringBefore('|').toLongOrNull() ?: 0L
    }

    fun save(context: Context, key: String, positionMs: Long, durationMs: Long) {
        if (positionMs < MIN_POSITION_MS) return
        // Perto do fim (últimos 5% ou 2 minutos): considera assistido e esquece
        if (durationMs > 0 && (positionMs > durationMs * 0.95 || durationMs - positionMs < 120_000L)) {
            clear(context, key)
            return
        }
        val prefs = sp(context)
        prefs.edit().putString(key, "$positionMs|$durationMs|${System.currentTimeMillis()}").apply()
        if (prefs.all.size > MAX_ITEMS) prune(context)
    }

    fun clear(context: Context, key: String) {
        sp(context).edit().remove(key).apply()
    }

    /** Apaga os mais antigos quando passar do limite. */
    private fun prune(context: Context) {
        val prefs = sp(context)
        val byTime = prefs.all.entries.sortedBy { (it.value as? String)?.substringAfterLast('|')?.toLongOrNull() ?: 0L }
        val extra = byTime.size - MAX_ITEMS
        if (extra <= 0) return
        prefs.edit().apply { byTime.take(extra).forEach { remove(it.key) } }.apply()
    }

    /** "1:05:12" ou "12:34". */
    fun format(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }
}
