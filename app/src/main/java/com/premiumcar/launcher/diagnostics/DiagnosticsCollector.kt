package com.premiumcar.launcher.diagnostics

import android.app.Activity
import com.premiumcar.launcher.diagnostics.cartest.PlayerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Orquestra a coleta: leituras de disco/sistema em segundo plano, janela na thread principal. */
object DiagnosticsCollector {

    suspend fun collect(activity: Activity): DiagnosticReport {
        val app = activity.applicationContext
        val props = withContext(Dispatchers.IO) { SystemProperties.read() }
        val display = guarded("Tela") { DisplayInfoCollector.collect(activity, props) }
        return withContext(Dispatchers.IO) {
            DiagnosticReport(
                mapOf(
                    DiagnosticTab.CAR_TESTS to guarded("Testes no carro") { PlayerChecks.sections(app) + PlayerInfo.sections(app) },
                    DiagnosticTab.SYSTEM to guarded("Sistema") { SystemInfoCollector.collect(app, props) },
                    DiagnosticTab.DISPLAY to display,
                    DiagnosticTab.APPS to guarded("Aplicativos") { AppsInfoCollector.collect(app) },
                    DiagnosticTab.FEATURES to guarded("Recursos") { FeaturesCollector.collect(app) },
                    DiagnosticTab.PROPERTIES to listOf(SystemProperties.toSection(props)),
                ),
            )
        }
    }

    private inline fun guarded(title: String, block: () -> List<InfoSection>): List<InfoSection> =
        try {
            block()
        } catch (t: Throwable) {
            listOf(errorSection(title, t))
        }
}
