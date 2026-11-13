package com.premiumcar.launcher.launcher

import android.content.Context
import androidx.core.content.edit

/**
 * Telas do ZLINK aprendidas ([ZlinkScreens]), guardadas no app para valerem depois de a central
 * dormir ou o Nyx Car ser reaberto (0.4.11). Só resumos de 8 caracteres da estrutura das telas.
 */
internal object ZlinkScreenStore {

    private const val PREFS = "nyx_carplay_screens"
    private const val WAIT = "wait"
    private const val CARPLAY = "carplay"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): ZlinkScreens {
        val prefs = prefs(context)
        return ZlinkScreens(prefs.getStringSet(WAIT, null).orEmpty(), prefs.getStringSet(CARPLAY, null).orEmpty())
    }

    fun save(context: Context, screens: ZlinkScreens) {
        prefs(context).edit {
            putStringSet(WAIT, screens.waits)
            putStringSet(CARPLAY, screens.carPlays)
        }
        screens.saved()
    }

    /** Quantas telas de espera e do CarPlay já foram aprendidas (para o relatório). */
    fun counts(context: Context): Pair<Int, Int> {
        val prefs = prefs(context)
        return prefs.getStringSet(WAIT, null).orEmpty().size to prefs.getStringSet(CARPLAY, null).orEmpty().size
    }
}
