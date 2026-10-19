package com.meuiptv.player

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import java.util.Locale

/** Tela de Configurações: teste de internet, formato dos canais, próximo episódio, controle dos pais e cache. */
@OptIn(ExperimentalCoilApi::class)
class SettingsActivity : AppCompatActivity() {

    private lateinit var cacheInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Formato dos canais ao vivo
        val ts = findViewById<RadioButton>(R.id.fmt_ts)
        val hls = findViewById<RadioButton>(R.id.fmt_hls)
        if (Settings.liveFormat == "hls") hls.isChecked = true else ts.isChecked = true
        findViewById<RadioGroup>(R.id.fmt_group).setOnCheckedChangeListener { _, checkedId ->
            val format = if (checkedId == R.id.fmt_hls) "hls" else "ts"
            if (format != Settings.liveFormat) {
                Settings.liveFormat = format
                Repository.refresh() // as listas são montadas de novo com o formato novo
                toast(if (format == "hls") "Canais ao vivo em HLS" else "Canais ao vivo em MPEG-TS")
            }
        }
        ts.requestFocus()

        // Próximo episódio automático
        val autoNext = findViewById<CheckBox>(R.id.auto_next)
        autoNext.isChecked = Settings.autoNextEpisode
        autoNext.setOnCheckedChangeListener { _, checked -> Settings.autoNextEpisode = checked }

        // Controle dos pais
        val parental = findViewById<CheckBox>(R.id.parental)
        parental.isChecked = Settings.parentalOn
        parental.setOnClickListener {
            if (parental.isChecked) {
                // Ligando: se ainda usa a senha padrão, pede para criar uma
                if (Settings.pin == Settings.DEFAULT_PIN) {
                    parental.isChecked = false
                    askPin("Crie uma senha de 4 números") { pin ->
                        if (pin.length == 4) {
                            Settings.pin = pin
                            enableParental(parental)
                        } else {
                            toast("A senha precisa ter 4 números")
                        }
                    }
                } else {
                    enableParental(parental)
                }
            } else {
                // Desligando: precisa da senha
                parental.isChecked = true
                checkPin {
                    Settings.parentalOn = false
                    parental.isChecked = false
                    toast("Controle dos pais desativado")
                }
            }
        }

        findViewById<Button>(R.id.change_pin).setOnClickListener {
            val change = {
                askPin("Nova senha (4 números)") { pin ->
                    if (pin.length == 4) {
                        Settings.pin = pin
                        toast("Senha alterada")
                    } else {
                        toast("A senha precisa ter 4 números")
                    }
                }
            }
            if (Settings.pin == Settings.DEFAULT_PIN) change() else checkPin { change() }
        }

        // Teste de velocidade da internet
        val speedButton = findViewById<Button>(R.id.speed_test)
        val speedResult = findViewById<TextView>(R.id.speed_result)
        speedButton.setOnClickListener { runSpeedTest(speedButton, speedResult) }

        // Cache
        cacheInfo = findViewById(R.id.cache_info)
        updateCacheInfo()
        findViewById<Button>(R.id.clear_cache).setOnClickListener {
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            Repository.refresh()
            updateCacheInfo()
            toast("Cache limpo!")
        }

        // Versão
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        findViewById<TextView>(R.id.about).text = "${getString(R.string.app_name)} · versão ${version ?: "-"}"
    }

    private fun runSpeedTest(button: Button, result: TextView) {
        val br = Locale("pt", "BR")
        button.isEnabled = false
        button.text = "Testando..."
        result.text = "Medindo o tempo de resposta..."
        lifecycleScope.launch {
            try {
                val ping = withContext(Dispatchers.IO) { SpeedTest.ping() }
                result.text = "Ping: $ping ms\nMedindo a velocidade..."
                val mbps = withContext(Dispatchers.IO) {
                    SpeedTest.download { partial ->
                        runOnUiThread {
                            result.text = String.format(br, "Ping: %d ms\nVelocidade: %.1f Mbps...", ping, partial)
                        }
                    }
                }
                val server = withContext(Dispatchers.IO) { SpeedTest.serverPing() }
                val lines = mutableListOf(
                    String.format(br, "Velocidade: %.1f Mbps", mbps),
                    "Ping: $ping ms",
                )
                if (server != null) lines += "Servidor dos canais respondeu em $server ms"
                lines += ""
                lines += SpeedTest.verdict(mbps)
                result.text = lines.joinToString("\n")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                result.text = "Não foi possível fazer o teste: ${friendlyError(e)}"
            } finally {
                button.isEnabled = true
                button.text = "Testar de novo"
            }
        }
    }

    private fun enableParental(box: CheckBox) {
        Settings.parentalOn = true
        Session.adultUnlocked = false
        box.isChecked = true
        toast("Controle dos pais ativado")
    }

    private fun updateCacheInfo() {
        val bytes = imageLoader.diskCache?.size ?: 0L
        val mb = bytes / (1024.0 * 1024.0)
        cacheInfo.text = String.format(Locale("pt", "BR"), "Imagens guardadas: %.1f MB (limite 50 MB)", mb)
    }
}
