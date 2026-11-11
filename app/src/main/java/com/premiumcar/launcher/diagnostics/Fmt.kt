package com.premiumcar.launcher.diagnostics

import android.content.Context
import java.util.Locale

/** Formatação em pt-BR usada na tela e no relatório. */
object Fmt {
    private val locale: Locale = Locale.forLanguageTag("pt-BR")

    fun decimal(value: Double, digits: Int = 1): String = String.format(locale, "%.${digits}f", value)

    fun decimal(value: Float, digits: Int = 1): String = decimal(value.toDouble(), digits)

    fun bytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "${decimal(bytes / 1_073_741_824.0, 2)} GB"
        bytes >= 1L shl 20 -> "${decimal(bytes / 1_048_576.0)} MB"
        else -> "${bytes / 1024} kB"
    }

    /** Converte kB em MB; valores negativos significam "indisponível". */
    fun kb(kilobytes: Int): String = if (kilobytes < 0) "—" else "${decimal(kilobytes / 1024.0)} MB"
}

object AppInfo {
    fun versionName(context: Context): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}
