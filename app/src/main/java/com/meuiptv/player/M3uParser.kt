package com.meuiptv.player

import java.io.BufferedReader

/** Lê listas .m3u / .m3u8 no formato #EXTINF usado pelos provedores de IPTV. */
object M3uParser {

    private val logoRegex = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val groupRegex = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val tvgNameRegex = Regex("tvg-name=\"([^\"]*)\"", RegexOption.IGNORE_CASE)

    fun parse(reader: BufferedReader): List<Item> {
        val out = ArrayList<Item>()
        var name: String? = null
        var logo: String? = null
        var group = "Sem categoria"

        var line = reader.readLine()
        while (line != null) {
            val l = line.trim()
            if (l.startsWith("#EXTINF", ignoreCase = true)) {
                logo = logoRegex.find(l)?.groupValues?.get(1)?.ifBlank { null }
                group = groupRegex.find(l)?.groupValues?.get(1)?.ifBlank { null } ?: "Sem categoria"
                name = titleOf(l).ifBlank {
                    tvgNameRegex.find(l)?.groupValues?.get(1)?.ifBlank { null } ?: "Sem nome"
                }
            } else if (l.isNotEmpty() && !l.startsWith("#") && name != null) {
                out += Item(id = l, name = name, icon = logo, kind = kindOf(l), url = l, categoryId = group)
                name = null
            }
            line = reader.readLine()
        }
        return out
    }

    /** O nome fica depois da primeira vírgula que não está dentro de aspas. */
    private fun titleOf(line: String): String {
        var inQuotes = false
        for (i in line.indices) {
            when (line[i]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return line.substring(i + 1).trim()
            }
        }
        return ""
    }

    private fun kindOf(url: String): String {
        val u = url.lowercase()
        return when {
            "/movie/" in u -> Kind.MOVIE
            "/series/" in u -> Kind.SERIES
            u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") -> Kind.MOVIE
            else -> Kind.LIVE
        }
    }
}
