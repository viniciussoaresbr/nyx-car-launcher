package com.premiumcar.launcher.launcher

import androidx.annotation.DrawableRes
import java.text.Collator
import java.util.Locale

/** Aparência de um app conhecido na lista "Todos": id do Nyx Car, ícone de traço e nome. */
data class AppLook(val id: String, val target: AppTarget.App, @DrawableRes val icon: Int, val label: String)

/** Um app da lista "Todos", com o nome e o ícone exibidos; [lookId] se for um app conhecido. */
data class AppItem(val entry: LauncherEntry, val label: String, @DrawableRes val icon: Int, val lookId: String? = null) {
    /** Como este app fica guardado nos favoritos. */
    val favoriteKey: FavoriteKey
        get() = lookId?.let { FavoriteKey.Known(it) } ?: FavoriteKey.Other(entry.packageName, entry.className, label)
}

/**
 * Lista "Todos" da aba Aplicativos (0.4.4). Lógica pura, testada na JVM.
 *
 * - Entram os apps com ícone na lista do sistema, em ordem alfabética (sem diferenciar
 *   acentos nem maiúsculas).
 * - Os apps conhecidos usam o ícone de traço e o nome dos atalhos do Nyx Car ("CarPlay" em vez
 *   de "ZLINK5"); os desconhecidos, um ícone de traço genérico e o nome do sistema.
 * - Ficam de fora: o próprio Nyx Car, entradas sem nome, os atualizadores de firmware, MCU e
 *   CAN (um toque errado leva a uma atualização; plano, seção 9), o Theme e a "Música USB" do
 *   player original (pedidos do usuário). Do player original, só o Vídeo aparece.
 */
object AllApps {

    /** Pacotes que nunca aparecem na lista. */
    val HIDDEN_PACKAGES = setOf(
        "com.hcn.autoupdate", // "atualização"
        "com.hcn.canupgrade", // "Atualização de Rede Can"
        "com.hcn.mcuupgrade", // "Atualização Mcu"
        "com.hcn.themepicker", // "Theme" (pedido do usuário, 11/11/2026)
    )

    private val VIDEO_WORDS = listOf("vídeo", "video")

    fun build(
        entries: List<LauncherEntry>,
        looks: List<AppLook>,
        ownHome: String,
        @DrawableRes genericIcon: Int,
        locale: Locale = Locale.forLanguageTag("pt-BR"),
    ): List<AppItem> {
        val visible = entries.filterNot { isHidden(it, ownHome) }.distinctBy { it.packageName to it.className }
        // Cada entrada recebe no máximo uma aparência; a primeira que a escolher fica com ela.
        val lookOf = HashMap<LauncherEntry, AppLook>()
        looks.forEach { look ->
            EntrySelection.choose(visible.filterNot { it in lookOf }, look.target)?.let { lookOf[it] = look }
        }
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return visible
            .map { entry ->
                val look = lookOf[entry]
                AppItem(entry, look?.label ?: entry.label.trim(), look?.icon ?: genericIcon, look?.id)
            }
            .sortedWith(compareBy<AppItem, String>(collator) { it.label }.thenBy { it.entry.packageName })
    }

    fun isHidden(entry: LauncherEntry, ownHome: String): Boolean =
        entry.label.isBlank() ||
            entry.packageName in HIDDEN_PACKAGES ||
            entry.className == ownHome ||
            isUsbMusic(entry)

    /** "Música USB" nunca aparece: do player original, só entra a entrada do Vídeo. */
    private fun isUsbMusic(entry: LauncherEntry): Boolean {
        if (entry.packageName != CarApps.USB_MUSIC.packageName) return false
        val label = entry.label.lowercase(Locale.ROOT)
        return VIDEO_WORDS.none { label.contains(it) }
    }
}
