package org.danielmarques.teletv

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.iniciar(this)
        Biblioteca.iniciar(this)
        Tg.iniciar(this)
        registerActivityLifecycleCallbacks(Trava)
    }
}

/** Pede a senha ao abrir o app e trava de novo quando ele sai da tela. */
object Trava : Application.ActivityLifecycleCallbacks {
    var liberado = false
    private var visiveis = 0

    override fun onActivityStarted(a: Activity) {
        visiveis++
        if (Prefs.temSenha && !liberado && a !is PinActivity) {
            a.startActivity(Intent(a, PinActivity::class.java).putExtra("modo", PinActivity.ABRIR))
        }
    }

    override fun onActivityStopped(a: Activity) {
        if (--visiveis == 0) liberado = false
    }

    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityResumed(a: Activity) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}
