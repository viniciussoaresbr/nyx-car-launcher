package com.premiumcar.launcher.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Launchers, apps instalados e atalhos do sistema. Descobre os nomes de pacote
 * (ZLINK, Bluetooth, música…) necessários para os atalhos da Fase 4.
 */
@Suppress("DEPRECATION") // APIs com flags Int: são as únicas disponíveis no Android antigo.
object AppsInfoCollector {

    private class App(val label: String, val packageName: String, val system: Boolean, val version: String?)

    fun collect(context: Context): List<InfoSection> {
        val pm = context.packageManager
        val apps = launchableApps(pm)
        // O próprio launcher não é candidato a atalho ("car" no pacote casaria com "Veículo").
        val others = apps.filter { it.packageName != context.packageName }
        return listOf(
            LauncherChecks.section(context),
            DeveloperOptions.section(context),
            home(pm),
            candidates(others),
            shortcuts(pm),
            media(context, pm),
            allApps(apps),
        )
    }

    private fun launchableApps(pm: PackageManager): List<App> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).map { info ->
            val activity = info.activityInfo
            App(
                label = info.loadLabel(pm).toString(),
                packageName = activity.packageName,
                system = activity.applicationInfo.isSystem(),
                version = runCatching { pm.getPackageInfo(activity.packageName, 0).versionName }.getOrNull(),
            )
        }.sortedBy { it.label.lowercase() }
    }

    private fun home(pm: PackageManager) = section("Tela inicial (launcher)") {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val default = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        highlight(
            "Launcher padrão atual",
            when {
                default == null -> "não resolvido"
                default.activityInfo.packageName == "android" -> "nenhum definido (o sistema pergunta qual usar)"
                else -> describe(pm, default)
            },
        )
        pm.queryIntentActivities(intent, 0).forEachIndexed { index, info ->
            val origin = if (info.activityInfo.applicationInfo.isSystem()) " · sistema" else ""
            add("Launcher instalado ${index + 1}", describe(pm, info) + origin)
        }
        val settings = pm.resolveActivity(Intent(Settings.ACTION_HOME_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY)
        add(
            "Tela \"Escolher tela inicial\"",
            if (settings != null) "disponível" else "indisponível",
            if (settings != null) EntryLevel.NORMAL else EntryLevel.WARNING,
        )
        note = "Anote o pacote do launcher original: ele é a referência para recuperação (ver BUILD.md)."
    }

    private fun candidates(apps: List<App>) = section("Candidatos para atalhos") {
        AppCategory.entries.forEach { category ->
            val matches = apps.filter { category.matches(it.label, it.packageName) }
            val value = if (matches.isEmpty()) {
                "nenhum encontrado"
            } else {
                matches.joinToString("\n") { "${it.label} — ${it.packageName}" }
            }
            val found = category == AppCategory.PROJECTION && matches.isNotEmpty()
            add(category.title, value, if (found) EntryLevel.HIGHLIGHT else EntryLevel.NORMAL)
        }
        note = "Classificação automática por nome e pacote; confirme na lista completa de aplicativos."
    }

    private fun shortcuts(pm: PackageManager) = section("Atalhos do sistema (intents)") {
        intentChecks().forEach { (label, intent) ->
            val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            when {
                resolved == null -> warn(label, "indisponível")
                resolved.activityInfo.packageName == "android" -> {
                    val options = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).size
                    add(label, "$options opções — o sistema pergunta qual usar")
                }
                else -> add(label, describe(pm, resolved))
            }
        }
        note = "\"Indisponível\" significa que o atalho não pode ser aberto por esta via neste firmware."
    }

    private fun intentChecks(): List<Pair<String, Intent>> = buildList {
        add("Configurações do Android" to Intent(Settings.ACTION_SETTINGS))
        add("Bluetooth" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        add("Wi-Fi" to Intent(Settings.ACTION_WIFI_SETTINGS))
        add("Data e hora" to Intent(Settings.ACTION_DATE_SETTINGS))
        add("Tela" to Intent(Settings.ACTION_DISPLAY_SETTINGS))
        add("Som" to Intent(Settings.ACTION_SOUND_SETTINGS))
        add("Lista de aplicativos" to Intent(Settings.ACTION_APPLICATION_SETTINGS))
        add("Escolher tela inicial" to Intent(Settings.ACTION_HOME_SETTINGS))
        if (Build.VERSION.SDK_INT >= 24) add("Apps padrão" to Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        add(
            "Fontes desconhecidas" to
                if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) else Intent(Settings.ACTION_SECURITY_SETTINGS),
        )
        add("Acesso a notificações" to Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        add("Acesso ao uso de apps" to Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        add("Player de música padrão" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
        add("App de mapas padrão" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS))
        add("Navegação (geo:)" to Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=posto")))
        add("Discador" to Intent(Intent.ACTION_DIAL))
        add("Navegador" to Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))
        add(
            "Seletor de arquivos (\"Salvar em…\")" to
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain"),
        )
    }

    private fun media(context: Context, pm: PackageManager) = section("Mídia (cartão de música)") {
        val browsers = pm.queryIntentServices(Intent("android.media.browse.MediaBrowserService"), 0)
        add(
            "Apps com MediaBrowserService",
            if (browsers.isEmpty()) "nenhum" else browsers.joinToString("\n") { describe(pm, it) },
        )
        val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
        add("Modo low-RAM (Android Go)", if (lowRam) "sim" else "não", if (lowRam) EntryLevel.WARNING else EntryLevel.NORMAL)
        note = "Mostrar a faixa que toca em outros apps exige \"Acesso a notificações\" (ver Atalhos do sistema). " +
            "Em modo low-RAM o Android costuma bloquear esse acesso — a confirmar na Fase 4."
    }

    private fun allApps(apps: List<App>) = section("Aplicativos com ícone (${apps.size})") {
        apps.forEach { app ->
            val details = listOfNotNull(app.packageName, app.version?.let { "v$it" }, "sistema".takeIf { app.system })
            add(app.label, details.joinToString(" · "))
        }
    }

    private fun describe(pm: PackageManager, info: ResolveInfo): String {
        val component: ComponentInfo = info.activityInfo ?: info.serviceInfo
        val shortName = component.name.removePrefix(component.packageName)
        return "${info.loadLabel(pm)} — ${component.packageName}/$shortName"
    }

    private fun ApplicationInfo.isSystem() = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
}
