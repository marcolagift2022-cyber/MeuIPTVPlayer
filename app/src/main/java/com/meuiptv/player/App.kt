package com.meuiptv.player

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Se o Android fechou o app em segundo plano, recarrega o login salvo
        Prefs(this).account()?.let { Repository.init(it) }
    }
}
