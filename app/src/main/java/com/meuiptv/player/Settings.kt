package com.meuiptv.player

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog

/** Preferências do app (tela de Configurações). Inicializado em App.onCreate. */
object Settings {

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("meu_iptv", Context.MODE_PRIVATE)
    }

    /** "ts" (MPEG-TS) ou "hls" (.m3u8) para os canais ao vivo. */
    var liveFormat: String
        get() = sp.getString("live_format", "ts") ?: "ts"
        set(v) = sp.edit().putString("live_format", v).apply()

    val liveExtension: String get() = if (liveFormat == "hls") "m3u8" else "ts"

    var autoNextEpisode: Boolean
        get() = sp.getBoolean("auto_next", true)
        set(v) = sp.edit().putBoolean("auto_next", v).apply()

    var parentalOn: Boolean
        get() = sp.getBoolean("parental", false)
        set(v) = sp.edit().putBoolean("parental", v).apply()

    var pin: String
        get() = sp.getString("pin", DEFAULT_PIN) ?: DEFAULT_PIN
        set(v) = sp.edit().putString("pin", v).apply()

    /** Vencimento da assinatura (segundos desde 1970). 0 = sem data. */
    var expDate: Long
        get() = sp.getLong("exp_date", 0L)
        set(v) = sp.edit().putLong("exp_date", v).apply()

    /** User-agent definido no config.json (vazio = padrão do app). */
    var userAgent: String
        get() = sp.getString("user_agent", "") ?: ""
        set(v) = sp.edit().putString("user_agent", v).apply()

    const val DEFAULT_PIN = "0000"
}

/** Estado que vale só enquanto o app está aberto. */
object Session {
    /** Depois de digitar a senha uma vez, as categorias adultas ficam liberadas até fechar o app. */
    var adultUnlocked = false
}

private val ADULT_WORDS = listOf("adult", "xxx", "+18", "18+", "porn")

/** Categoria com conteúdo adulto (pelo nome). */
fun isAdult(name: String): Boolean {
    val n = name.lowercase()
    return ADULT_WORDS.any { it in n }
}

/** Precisa esconder/trancar conteúdo adulto agora? */
fun adultLocked(): Boolean = Settings.parentalOn && !Session.adultUnlocked

/** Janela que pede uma senha de 4 números. */
fun Activity.askPin(title: String, onOk: (String) -> Unit) {
    val input = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(InputFilter.LengthFilter(4))
        hint = "• • • •"
        gravity = Gravity.CENTER
        textSize = 26f
    }
    val pad = (24 * resources.displayMetrics.density).toInt()
    val box = FrameLayout(this).apply {
        setPadding(pad, pad / 2, pad, 0)
        addView(input)
    }
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(box)
        .setPositiveButton("OK") { _, _ -> onOk(input.text.toString()) }
        .setNegativeButton("Cancelar", null)
        .show()
    input.requestFocus()
}

/** Pede a senha do controle dos pais e só continua se estiver certa. */
fun Activity.checkPin(onSuccess: () -> Unit) {
    askPin("Senha do controle dos pais") { typed ->
        if (typed == Settings.pin) onSuccess() else toast("Senha incorreta")
    }
}
