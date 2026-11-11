package com.premiumcar.launcher.design

/**
 * Paleta do Design System, aprovada na calibração feita na central (09/11/2026).
 *
 * A fonte principal é `res/values/colors.xml` (usada pelas Views e pelos temas).
 * Este objeto espelha os valores para código Kotlin (painel de diagnóstico em
 * Compose e testes de fluidez). Um teste automatizado garante que os dois coincidem.
 */
object PcPalette {
    const val BACKGROUND = 0xFF0E1116.toInt()
    const val SURFACE = 0xFF161B21.toInt()
    const val SURFACE_HIGH = 0xFF1E252D.toInt()
    const val SURFACE_HIGHEST = 0xFF27303A.toInt()
    const val OUTLINE = 0xFF2F3943.toInt()

    const val TEXT_PRIMARY = 0xFFE8ECEF.toInt()
    const val TEXT_SECONDARY = 0xFF8D9AA6.toInt()
    const val TEXT_DISABLED = 0xFF5A6672.toInt()

    const val ACCENT = 0xFF2CC8D8.toInt()
    const val ACCENT_DIM = 0xFF1A7F8A.toInt()
    const val ACCENT_CONTAINER = 0xFF1A3E46.toInt()
    const val ON_ACCENT = 0xFF00262B.toInt()

    const val WARNING = 0xFFE3B341.toInt()
    const val ERROR = 0xFFE5534B.toInt()
    const val SUCCESS = 0xFF3FB950.toInt()
}
