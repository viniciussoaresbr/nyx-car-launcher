package com.premiumcar.launcher.app

import android.app.Application
import com.premiumcar.launcher.launcher.CarPlayReturn
import com.premiumcar.launcher.perf.StartupTimeline
import com.premiumcar.launcher.system.CrashRecorder
import java.io.File

/**
 * Ponto de entrada de cada processo do app. Deve permanecer leve: qualquer
 * inicialização pesada aqui atrasa a abertura em hardware limitado.
 */
class NyxCarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StartupTimeline.markApplicationCreated()
        CrashRecorder.install(this) // só registra um tratador: nada é lido nem gravado agora
        // Volta ao Nyx Car quando o CarPlay termina (0.4.5): só registra avisos do sistema, e só
        // no processo principal (os testes de fluidez rodam em processos separados).
        if (isMainProcess()) CarPlayReturn.install(this)
    }

    private fun isMainProcess(): Boolean =
        runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000').trim() == packageName }
            .getOrDefault(false)
}
