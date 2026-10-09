package com.meuiptv.player

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Atualização pelo próprio app.
 * Quando o "versao" do config.json for maior que a versão instalada, aparece o aviso;
 * o app baixa o APK novo e abre a tela de instalação do Android.
 */
object Updater {

    /** APK baixado esperando a permissão de "instalar apps desconhecidos". */
    private var pendingFile: File? = null
    private var dialogShowing = false

    fun installedVersion(activity: AppCompatActivity): Long = try {
        PackageInfoCompat.getLongVersionCode(activity.packageManager.getPackageInfo(activity.packageName, 0))
    } catch (e: Exception) {
        0L
    }

    /** Mostra o aviso se houver versão nova. Chame no menu principal. */
    fun checkAndPrompt(activity: AppCompatActivity) {
        if (dialogShowing) return
        val config = RemoteConfig.cached(activity) ?: return
        if (config.latestVersion <= installedVersion(activity)) return

        val builder = AlertDialog.Builder(activity)
            .setTitle("Nova versão disponível!")
            .setMessage(
                config.updateMessage.ifBlank {
                    "Uma nova versão do ${activity.getString(R.string.app_name)} está disponível, com melhorias e correções."
                }
            )
            .setPositiveButton("Atualizar agora") { _, _ ->
                dialogShowing = false
                download(activity, config)
            }
            .setOnDismissListener { dialogShowing = false }
        if (config.updateRequired) {
            builder.setCancelable(false) // atualização obrigatória: sem "Depois"
        } else {
            builder.setNegativeButton("Depois", null)
        }
        dialogShowing = true
        builder.show()
    }

    /** Volta do ajuste de permissão: se já pode instalar, continua a instalação. */
    fun resumePending(activity: AppCompatActivity) {
        val file = pendingFile ?: return
        if (canInstall(activity)) {
            pendingFile = null
            install(activity, file)
        }
    }

    private fun download(activity: AppCompatActivity, config: RemoteConfig.Config) {
        val pad = (24 * activity.resources.displayMetrics.density).toInt()
        val label = TextView(activity).apply {
            text = "Baixando a atualização..."
            textSize = 16f
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(label)
            addView(bar)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Atualizando")
            .setView(box)
            .setCancelable(false)
            .show()

        activity.lifecycleScope.launch {
            val dir = if (Build.VERSION.SDK_INT < 24) activity.externalCacheDir ?: activity.cacheDir else activity.cacheDir
            val file = File(dir, "updates/atualizacao.apk")
            val ok = try {
                withContext(Dispatchers.IO) {
                    Http.download(config.apkUrl, file) { pct ->
                        bar.post {
                            if (pct >= 0) {
                                bar.isIndeterminate = false
                                bar.progress = pct
                                label.text = "Baixando a atualização... $pct%"
                            }
                        }
                    }
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                activity.toast("Não foi possível baixar: ${friendlyError(e)}")
                false
            }
            dialog.dismiss()
            if (ok) {
                install(activity, file)
            } else if (config.updateRequired) {
                checkAndPrompt(activity)
            }
        }
    }

    private fun canInstall(activity: AppCompatActivity): Boolean =
        Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()

    private fun install(activity: AppCompatActivity, file: File) {
        if (!canInstall(activity)) {
            // Android 8+: precisa liberar "instalar apps desconhecidos" para este app (só na primeira vez)
            pendingFile = file
            activity.toast("Permita a instalação para este app e volte")
            try {
                activity.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}"),
                    )
                )
            } catch (e: Exception) {
                activity.toast("Ative \"Fontes desconhecidas\" nas configurações do aparelho")
            }
            return
        }
        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            activity.toast("Não foi possível abrir o instalador: ${e.message}")
        }
    }
}
