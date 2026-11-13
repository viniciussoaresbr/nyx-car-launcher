package com.premiumcar.launcher.launcher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import java.util.Locale

/** Entrada (MAIN/LAUNCHER) de um app instalado, com o nome que o sistema exibe. */
data class LauncherEntry(val packageName: String, val className: String, val label: String)

/** Escolha da entrada certa de um pacote. Lógica pura, testada na JVM. */
object EntrySelection {

    fun choose(entries: List<LauncherEntry>, target: AppTarget.App): LauncherEntry? {
        val pool = entries.filter { it.packageName == target.packageName }
        if (pool.isEmpty()) return null
        val allowed = pool.filterNot { it.label.containsAny(target.labelExcludes) }
        val preferred = if (target.labelHints.isEmpty()) allowed else allowed.filter { it.label.containsAny(target.labelHints) }
        return preferred.firstOrNull() ?: allowed.firstOrNull() ?: pool.first()
    }

    private fun String.containsAny(words: List<String>): Boolean {
        val text = lowercase(Locale.ROOT)
        return words.any { text.contains(it.lowercase(Locale.ROOT)) }
    }
}

/**
 * Abre apps e telas do sistema pelas APIs normais do Android (plano, seção 5).
 * Só monta Intents e chama `startActivity`: nada é alterado nos apps abertos.
 */
class AppLauncher(context: Context) {

    private val context = context.applicationContext
    private val pm = context.packageManager

    /** Intent pronta para abrir [target], ou `null` se ele não existe nesta central. */
    fun resolve(target: AppTarget): Intent? = when (target) {
        is AppTarget.App -> EntrySelection.choose(entries(target.packageName), target)?.let(::intentFor)
        is AppTarget.SystemScreen -> (listOf(target.action) + target.alternatives)
            .map(::Intent)
            .firstOrNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY) != null }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        is AppTarget.Home -> {
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(target.packageName)
            pm.queryIntentActivities(query, 0).firstOrNull()?.activityInfo?.let { info ->
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(ComponentName(info.packageName, info.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /** Situação de um atalho: pronto para abrir, app desativado pelo usuário ou ausente. */
    enum class Availability { AVAILABLE, DISABLED, MISSING }

    fun availability(target: AppTarget): Availability = when {
        target is AppTarget.App && isDisabled(target.packageName) -> Availability.DISABLED
        resolve(target) != null -> Availability.AVAILABLE
        else -> Availability.MISSING
    }

    fun isAvailable(target: AppTarget): Boolean = availability(target) == Availability.AVAILABLE

    /**
     * O app existe, mas foi desativado (Configurações → Apps → Desativar). Só leitura: o
     * Nyx Car não consegue desativar nem ativar outro app (exige permissão de sistema).
     */
    fun isDisabled(packageName: String): Boolean = try {
        pm.getApplicationEnabledSetting(packageName) in DISABLED_STATES
    } catch (e: IllegalArgumentException) {
        false // não instalado
    }

    /** Abre [target]. Devolve `false` se ele não existe ou o sistema recusou. */
    fun launch(target: AppTarget, from: Context): Boolean = start(resolve(target), from)

    /** Abre uma entrada da lista "Todos" (0.4.4). */
    fun launch(entry: LauncherEntry, from: Context): Boolean = start(intentFor(entry), from)

    /**
     * Todas as entradas com ícone na lista de apps (MAIN/LAUNCHER), com o nome do sistema.
     * Apps desativados não aparecem. Rápido: só lê nomes, nenhum ícone é carregado.
     */
    fun allEntries(): List<LauncherEntry> = query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))

    private fun start(intent: Intent?, from: Context): Boolean {
        if (intent == null) return false
        // CarPlay sem iPhone conectado: "Aguardando o iPhone" no lugar da tela parada do ZLINK (0.4.8).
        if (intent.component?.packageName == CarApps.CARPLAY.packageName && CarPlayReturn.interceptCarPlay()) return true
        // O ZLINK aberto pelo usuário não é o ZLINK abrindo sozinho (tela de espera do Nyx Car, 0.4.7).
        CarPlayReturn.markUserLaunch()
        return try {
            from.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    // Como um launcher: traz a tarefa existente (ex.: CarPlay já conectado) para a frente.
    private fun intentFor(entry: LauncherEntry): Intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(entry.packageName, entry.className))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun entries(packageName: String): List<LauncherEntry> =
        query(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName))

    private fun query(intent: Intent): List<LauncherEntry> = pm.queryIntentActivities(intent, 0).map { info ->
        LauncherEntry(info.activityInfo.packageName, info.activityInfo.name, info.loadLabel(pm).toString())
    }

    companion object {
        private val DISABLED_STATES = setOf(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        )

        /** Página do app nas Configurações do Android, onde ficam "Desativar" e "Ativar". */
        fun detailsIntent(packageName: String): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
