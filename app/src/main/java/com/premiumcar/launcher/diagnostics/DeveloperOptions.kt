package com.premiumcar.launcher.diagnostics

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Acesso à tela padrão "Opções do desenvolvedor" do Android, usada para ligar o ADB.
 *
 * Neste firmware, o atalho de tocar 7 vezes em "Número da versão" foi desativado, mas a
 * tela pode continuar existindo. Abri-la não altera nada: qualquer mudança depende de o
 * usuário ligar as chaves manualmente, com a confirmação do próprio Android.
 */
object DeveloperOptions {

    private val settingsActivity = ComponentName(
        "com.android.settings",
        "com.android.settings.Settings\$DevelopmentSettingsActivity",
    )

    /** Primeiro a ação pública; depois a Activity do app Configurações, caso a ação não esteja registrada. */
    private fun candidates(): List<Intent> = listOf(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Intent.ACTION_MAIN).setComponent(settingsActivity),
    )

    /** Tenta abrir a tela e devolve a mensagem a exibir. */
    fun open(context: Context): String {
        for (intent in candidates()) {
            try {
                context.startActivity(intent)
                return "Opções do desenvolvedor abertas. Depois de voltar, toque em \"Atualizar status\"."
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        return "Este firmware não permite abrir as opções do desenvolvedor."
    }

    @Suppress("DEPRECATION")
    fun section(context: Context) = section("Depuração (ADB)") {
        val resolver = context.contentResolver
        val developerEnabled = Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val adbEnabled = Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1
        add("Opções do desenvolvedor", if (developerEnabled) "ativadas" else "desativadas")
        add(
            "Depuração USB (ADB)",
            if (adbEnabled) "LIGADA" else "desligada",
            if (adbEnabled) EntryLevel.HIGHLIGHT else EntryLevel.NORMAL,
        )
        val pm = context.packageManager
        val screen = candidates().firstNotNullOfOrNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) }
        if (screen == null) {
            warn("Tela de opções do desenvolvedor", "não encontrada neste firmware")
        } else {
            add("Tela de opções do desenvolvedor", "${screen.activityInfo.packageName}/${screen.activityInfo.name}")
        }
        note = "Com a depuração ligada, o PC conecta pela rede na porta 8555 (ver BUILD.md, seção 9)."
    }
}
