package com.premiumcar.launcher.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.premiumcar.launcher.R

/** Destinos da barra inferior, na ordem de exibição (plano, seção 5). */
enum class Destination(@StringRes val labelRes: Int, @DrawableRes val iconRes: Int) {
    HOME(R.string.nav_home, R.drawable.ic_home),
    MUSIC(R.string.nav_music, R.drawable.ic_music),
    APPS(R.string.nav_apps, R.drawable.ic_apps),
    CONNECTIVITY(R.string.nav_connectivity, R.drawable.ic_connectivity),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings),
}
