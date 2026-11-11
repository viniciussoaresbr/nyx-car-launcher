package com.premiumcar.launcher.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** Favoritos da aba Aplicativos (0.4.4): sempre 12, sem repetição, troca por lugar. */
class FavoritesTest {

    private fun known(vararg ids: String) = ids.map { FavoriteKey.Known(it) }

    private val defaults = known("radio", "video", "bluetooth", "carplay", "maps", "youtube", "camera", "car_info", "equalizer", "steering", "files", "play_store")
    private val knownIds = (defaults.map { it.appId } + listOf("chrome", "google")).toSet()

    @Test
    fun `trocar por um app novo substitui so aquele lugar`() {
        val result = Favorites.replace(defaults, 1, FavoriteKey.Known("chrome"))
        assertEquals("chrome", (result[1] as FavoriteKey.Known).appId)
        assertEquals(defaults.filterIndexed { i, _ -> i != 1 }, result.filterIndexed { i, _ -> i != 1 })
    }

    @Test
    fun `trocar por um app que ja e favorito troca os dois de lugar`() {
        val result = Favorites.replace(defaults, 0, FavoriteKey.Known("carplay"))
        assertEquals(known("carplay", "video", "bluetooth", "radio"), result.take(4))
        assertEquals(12, result.size)
        assertEquals(12, result.map { it.id }.distinct().size)
    }

    @Test
    fun `sem nada guardado vale o padrao`() {
        assertEquals(defaults, Favorites.normalize(emptyList(), defaults, knownIds))
    }

    @Test
    fun `lista guardada invalida e completada sem repetir`() {
        // Id que esta versão não conhece, repetido e lista curta: completa com os padrões.
        val stored = known("chrome", "sumiu", "chrome", "radio")
        val result = Favorites.normalize(stored, defaults, knownIds)
        assertEquals(12, result.size)
        assertEquals(known("chrome", "radio", "video", "bluetooth"), result.take(4))
        assertEquals(12, result.map { it.id }.distinct().size)
    }

    @Test
    fun `lista guardada longa demais fica com os 12 primeiros`() {
        val stored = known("chrome", "google") + defaults
        assertEquals(known("chrome", "google") + defaults.take(10), Favorites.normalize(stored, defaults, knownIds))
    }

    @Test
    fun `gravar e ler devolve a mesma lista, inclusive apps desconhecidos`() {
        val other = FavoriteKey.Other("com.exemplo.app", "com.exemplo.app.Main", "Meu\tApp")
        val list = Favorites.replace(defaults, 5, other)
        val decoded = Favorites.decode(Favorites.encode(list))
        assertEquals(list.map { it.id }, decoded.map { it.id })
        assertEquals("Meu App", (decoded[5] as FavoriteKey.Other).label)
        assertEquals(known("radio"), Favorites.decode("k:radio\nlixo\na:sem-campos\n"))
    }
}
