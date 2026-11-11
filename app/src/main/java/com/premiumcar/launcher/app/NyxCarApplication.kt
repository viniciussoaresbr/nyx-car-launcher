package com.premiumcar.launcher.app

import android.app.Application
import com.premiumcar.launcher.perf.StartupTimeline
import com.premiumcar.launcher.system.CrashRecorder

/**
 * Ponto de entrada de cada processo do app. Deve permanecer leve: qualquer
 * inicialização pesada aqui atrasa a abertura em hardware limitado.
 */
class NyxCarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StartupTimeline.markApplicationCreated()
        CrashRecorder.install(this) // só registra um tratador: nada é lido nem gravado agora
    }
}
