package com.meuiptv.player

import android.content.Context
import android.widget.Toast
import org.json.JSONException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

const val EXTRA_KIND = "kind"
const val EXTRA_ID = "id"
const val EXTRA_NAME = "name"
const val EXTRA_NOTICE = "notice"

/** Transforma erros técnicos em mensagens que o usuário entende. */
fun friendlyError(e: Throwable): String = when (e) {
    is UnknownHostException -> "Servidor não encontrado. Confira o endereço e a sua internet."
    is SocketTimeoutException -> "O servidor demorou demais para responder. Tente de novo."
    is ConnectException -> "Não foi possível conectar ao servidor."
    is SSLException -> "Erro de segurança (HTTPS) ao conectar. Tente o endereço com http://"
    is JSONException -> "O servidor respondeu em um formato inesperado."
    else -> e.message ?: "Erro desconhecido."
}

/** Aceita "servidor.com:8080" e completa para "http://servidor.com:8080". */
fun normalizeUrl(raw: String): String {
    var s = raw.trim()
    if (s.isEmpty()) return s
    if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
        s = "http://$s"
    }
    return s.trimEnd('/')
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
