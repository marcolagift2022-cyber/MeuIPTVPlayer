package com.meuiptv.player

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        // Se o Android fechou o app em segundo plano, recarrega o login salvo
        Prefs(this).account()?.let { Repository.init(it) }
        keepContentAwayFromSystemBars()
    }

    /**
     * A partir do Android 15 o app desenha por baixo da barra de status e da barra de navegação.
     * Aqui cada tela (menos o player, que é tela cheia) ganha um espaço para nada ficar escondido.
     */
    private fun keepContentAwayFromSystemBars() {
        if (Build.VERSION.SDK_INT < 35) return
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is PlayerActivity) return
                val content = activity.findViewById<View>(android.R.id.content) ?: return
                ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
                    val bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout() or
                            WindowInsetsCompat.Type.ime()
                    )
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    WindowInsetsCompat.CONSUMED
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /** Cache de logos e capas com limite (bom para TV Box com pouco espaço). */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                .build()
        }
        .crossfade(true)
        .build()
}
