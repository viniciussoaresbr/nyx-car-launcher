package com.premiumcar.launcher.diagnostics.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.premiumcar.launcher.design.NyxCarTheme
import com.premiumcar.launcher.diagnostics.cartest.CarTestLog
import com.premiumcar.launcher.perf.StartupTimeline
import com.premiumcar.launcher.player.MusicPlayer
import com.premiumcar.launcher.ui.StableSystemBars

/**
 * Painel de diagnóstico (ferramenta de desenvolvimento, em Compose).
 * Tem ícone e tarefa próprios ("Nyx Diagnóstico") para não se misturar com a Home.
 * Será portado para Views como a tela "Sobre / Diagnóstico" das Configurações.
 */
class DiagnosticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (StartupTimeline.markActivityCreated(SCREEN_NAME)) {
            StartupTimeline.trackFirstFrame(window.decorView)
        }
        // Recupera a linha do tempo se o Android encerrou o app no meio de um teste.
        CarTestLog.attach(this)
        MusicPlayer.init(this)
        setContent {
            NyxCarTheme {
                DiagnosticsApp(activity = this)
            }
        }
        // Como na Home (0.4.2): ao voltar do CarPlay (tela cheia, sem barra de status), o
        // conteúdo não desce quando a barra reaparece. Relatado pelo usuário em 12/11/2026.
        StableSystemBars.apply(window, findViewById(android.R.id.content))
    }

    companion object {
        const val SCREEN_NAME = "Diagnóstico (Compose)"
    }
}
