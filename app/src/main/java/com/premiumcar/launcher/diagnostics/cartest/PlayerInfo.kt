package com.premiumcar.launcher.diagnostics.cartest

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.media.MediaBrowserService
import com.premiumcar.launcher.diagnostics.EntryLevel
import com.premiumcar.launcher.diagnostics.InfoSection
import com.premiumcar.launcher.diagnostics.section
import com.premiumcar.launcher.launcher.AppLauncher

/**
 * O que o Android informa sobre o player USB e os caminhos de controle de mídia.
 * Metadados públicos do sistema (PackageManager): nada é descompilado nem alterado.
 */
object PlayerInfo {

    fun sections(context: Context): List<InfoSection> = listOf(player(context), mediaButtonReceivers(context), sessions(context))

    @Suppress("DEPRECATION") // getPackageInfo(String, Int): a forma nova só existe no Android 13+.
    private fun player(context: Context) = section("Player USB (${MediaCommands.PLAYER_PACKAGE})") {
        val pm = context.packageManager
        val info = runCatching { pm.getPackageInfo(MediaCommands.PLAYER_PACKAGE, PackageManager.GET_SERVICES) }.getOrNull()
        if (info == null) {
            warn("Instalado", "não")
            return@section
        }
        add("Versão", info.versionName)
        if (AppLauncher(context).isDisabled(MediaCommands.PLAYER_PACKAGE)) {
            highlight("Situação", "desativado pelo usuário (não abre ao conectar o pendrive; reative em Configurações → Apps)")
        } else {
            add("Situação", "ativado")
        }
        val receivers = MediaCommands.playerReceivers(context)
        add(
            "Receptor de botões de mídia",
            receivers.joinToString("\n") { it.className }.ifEmpty { "nenhum" },
            if (receivers.isEmpty()) EntryLevel.WARNING else EntryLevel.HIGHLIGHT,
        )
        val browser = pm.queryIntentServices(
            Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(MediaCommands.PLAYER_PACKAGE), 0,
        )
        add("Serviço de navegação de mídia (MediaBrowserService)", if (browser.isEmpty()) "não" else "sim")
        val services = info.services.orEmpty()
        add(
            "Serviços declarados (${services.size})",
            services.joinToString("\n") { "${it.name}${if (it.exported) " · exportado" else ""}" }.ifEmpty { "nenhum" },
        )
        note = "Sem serviço, um player normalmente não consegue tocar com a tela dele fechada. " +
            "Sem receptor nem sessão de mídia, ele não recebe comandos de outros apps."
    }

    private fun mediaButtonReceivers(context: Context) = section("Receptores de botões de mídia no sistema") {
        val receivers = context.packageManager.queryBroadcastReceivers(Intent(Intent.ACTION_MEDIA_BUTTON), 0)
        if (receivers.isEmpty()) add("Receptores", "nenhum")
        receivers.forEachIndexed { index, resolve ->
            add("Receptor ${index + 1}", "${resolve.activityInfo.packageName}/${resolve.activityInfo.name}")
        }
        note = "Apps que aceitam teclas de mídia (volante, fones) pelo caminho antigo do Android."
    }

    private fun sessions(context: Context) = section("Sessões de mídia ativas") {
        val summary = MediaSessionAccess.summary(context)
        if (summary == null) {
            warn("Acesso a notificações", "não concedido (opcional; ver cartão acima)")
        } else {
            add("Acesso a notificações", "concedido")
            add("Sessões agora", summary)
        }
        note = "Com o acesso, dá para saber se o player USB, o Bluetooth e o CarPlay publicam " +
            "estado e faixa, o que permite sincronizar o cartão de música."
    }
}
