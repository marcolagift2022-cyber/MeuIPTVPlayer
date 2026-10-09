package com.meuiptv.player

import android.app.Application
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
