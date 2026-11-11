package com.premiumcar.launcher.settings

import android.content.Context
import androidx.core.content.edit
import com.premiumcar.launcher.launcher.FavoriteKey
import com.premiumcar.launcher.launcher.Favorites
import com.premiumcar.launcher.ui.screens.TabContent

/**
 * Favoritos da aba Aplicativos guardados no aparelho (0.4.4). São 12 linhas curtas: como a
 * memória do player, ficam em `SharedPreferences`, gravadas com `apply()` (sem esperar o disco).
 * Sobrevivem a desligar a central e a atualizar o app; somem só se o app for desinstalado.
 */
class FavoritesStore(context: Context, private val defaults: List<FavoriteKey>, private val knownIds: Set<String>) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): List<FavoriteKey> = Favorites.normalize(Favorites.decode(prefs.getString(KEY, null)), defaults, knownIds)

    fun save(list: List<FavoriteKey>) {
        prefs.edit { putString(KEY, Favorites.encode(list)) }
    }

    /** Os favoritos padrão (os 12 atalhos de antes da 0.4.4). */
    val defaultList: List<FavoriteKey> get() = defaults

    companion object {
        private const val FILE_NAME = "favorites"
        private const val KEY = "apps_v1"

        /** Com os apps conhecidos e os favoritos padrão desta versão. */
        fun create(context: Context) = FavoritesStore(
            context,
            TabContent.defaultFavorites.map { FavoriteKey.Known(it) },
            TabContent.knownApps.map { it.id }.toSet(),
        )
    }
}
