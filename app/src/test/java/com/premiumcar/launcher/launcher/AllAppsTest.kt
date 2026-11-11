package com.premiumcar.launcher.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lista "Todos" da aba Aplicativos (0.4.4): o que entra, o que fica de fora e a ordem. */
class AllAppsTest {

    private val home = "com.premiumcar.launcher.app.MainActivity"
    private val generic = 0

    private fun entry(pkg: String, label: String, cls: String = "$pkg.Main") = LauncherEntry(pkg, cls, label)

    private fun build(entries: List<LauncherEntry>, looks: List<AppLook> = emptyList()) =
        AllApps.build(entries, looks, home, generic)

    @Test
    fun `atualizadores, Theme, o proprio launcher e entradas sem nome ficam de fora`() {
        val list = build(
            listOf(
                entry("com.hcn.autoupdate", "atualização"),
                entry("com.hcn.canupgrade", "Atualização de Rede Can"),
                entry("com.hcn.mcuupgrade", "Atualização Mcu"),
                entry("com.hcn.themepicker", "Theme"),
                entry("com.hcn.screensaver", " "),
                entry("com.premiumcar.launcher", "Nyx Car", home),
                entry("com.premiumcar.launcher", "Nyx Diagnóstico", "com.premiumcar.launcher.Diag"),
                entry("com.android.chrome", "Chrome"),
            ),
        )
        assertEquals(listOf("Chrome", "Nyx Diagnóstico"), list.map { it.label })
    }

    @Test
    fun `do player original so o Video aparece, nunca a Musica USB`() {
        val player = "com.hcn.AutoMediaPlayer"
        val list = build(listOf(entry(player, "Música", "m.Music"), entry(player, "Vídeo", "m.Video"), entry(player, "Music", "m.En")))
        assertEquals(listOf("Vídeo"), list.map { it.label })
    }

    @Test
    fun `apps conhecidos usam icone e nome do Nyx Car e os outros o generico`() {
        val list = build(
            listOf(entry("com.zjinnova.zlink", "ZLINK5"), entry("com.exemplo.novo", "App Novo"), entry("com.android.chrome", "Chrome")),
            looks = listOf(AppLook("carplay", CarApps.CARPLAY, 7, "CarPlay"), AppLook("chrome", CarApps.CHROME, 8, "Chrome")),
        )
        assertEquals(listOf("App Novo" to generic, "CarPlay" to 7, "Chrome" to 8), list.map { it.label to it.icon })
        assertEquals(listOf(null, "carplay", "chrome"), list.map { it.lookId })
        assertEquals(FavoriteKey.Other("com.exemplo.novo", "com.exemplo.novo.Main", "App Novo"), list[0].favoriteKey)
        assertEquals(FavoriteKey.Known("carplay"), list[1].favoriteKey)
    }

    @Test
    fun `cada entrada recebe uma aparencia so, mesmo com o mesmo pacote`() {
        val bt = "com.autochips.bluetooth"
        val list = build(
            listOf(entry(bt, "Bluetooth", "a.Main"), entry(bt, "BT Música", "a.Music")),
            looks = listOf(AppLook("bluetooth", CarApps.BLUETOOTH, 1, "Bluetooth"), AppLook("bt_music", CarApps.BT_MUSIC, 2, "BT Música")),
        )
        assertEquals(listOf("Bluetooth" to 1, "BT Música" to 2), list.map { it.label to it.icon })
    }

    @Test
    fun `ordem alfabetica ignora acentos e maiusculas`() {
        val list = build(listOf(entry("p.v", "Vídeo"), entry("p.c", "câmera"), entry("p.ve", "Veículo"), entry("p.a", "Arquivos")))
        assertEquals(listOf("Arquivos", "câmera", "Veículo", "Vídeo"), list.map { it.label })
        assertTrue(AllApps.isHidden(entry("com.hcn.mcuupgrade", "Atualização Mcu"), home))
        assertFalse(AllApps.isHidden(entry("com.hcn.gallery", "Scan de imagens"), home))
    }
}
